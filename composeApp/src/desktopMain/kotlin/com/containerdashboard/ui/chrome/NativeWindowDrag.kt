package com.containerdashboard.ui.chrome

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.slf4j.LoggerFactory

/**
 * Initiates a native macOS window drag by forwarding the current NSEvent to
 * `-[NSWindow performWindowDragWithEvent:]`.
 *
 * Why this exists: with `undecorated = true` the window has no OS title
 * bar, so drags go through Compose Desktop's `WindowDraggableArea`, which
 * handles them entirely in Kotlin — it reads AWT mouse deltas and calls
 * `frame.setLocation(x, y)`. On macOS multi-monitor setups, AWT's screen
 * coordinate mapping disagrees with AppKit's unified coordinate space
 * whenever the cursor crosses into a secondary display, so the window gets
 * clamped at the primary display's edge instead of following the cursor
 * across. Delegating to `performWindowDragWithEvent:` hands the drag to
 * AppKit's window server and multi-monitor just works.
 *
 * The trick is `-[NSApplication currentEvent]`, which returns the NSEvent
 * currently being processed by AppKit. When Compose/Skiko hands a
 * mouse-press to our pointer-input handler on the EDT, we're still inside
 * AppKit's event dispatch for the originating NSEvent, so `currentEvent`
 * still points at it. We grab it and pass it to the drag API.
 *
 * ── JBR thread contract ────────────────────────────────────────────────────
 * `performWindowDragWithEvent:` is an AppKit method that must be called on
 * the AppKit main thread. This call is safe because the JetBrains Runtime
 * (JBR) dispatches Compose pointer-input callbacks synchronously during
 * AppKit's own event-processing loop: the AWT Event Dispatch Thread (EDT) in
 * JBR is bridged to AppKit event delivery, so when a mouse-press reaches
 * [startDrag] we are already executing inside AppKit's event handler — on
 * the AppKit main thread — and `NSApplication.currentEvent` still points at
 * the originating NSEvent. This is an implementation detail of JBR, not a
 * Swing/AWT guarantee; a future JBR change could decouple the two threads.
 * The [startDrag] guard below checks `[NSThread isMainThread]` at runtime
 * and logs a warning (without crashing) if that contract ever breaks, so
 * the failure surfaces loudly rather than as undefined AppKit behaviour.
 * See `NativeMacApp.performOnMain()` for the explicit main-thread marshaling
 * pattern used when we cannot rely on already being on the main thread.
 * ──────────────────────────────────────────────────────────────────────────
 *
 * No-op on non-macOS platforms (returns `false` so the caller falls back
 * to `WindowDraggableArea`).
 */
object NativeWindowDrag {
    private val logger = LoggerFactory.getLogger(NativeWindowDrag::class.java)

    private val isMacOs: Boolean =
        System
            .getProperty("os.name")
            .orEmpty()
            .lowercase()
            .contains("mac")

    @Suppress("ktlint:standard:function-naming")
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?

        fun sel_registerName(name: String): Pointer?

        fun objc_msgSend(
            receiver: Pointer,
            selector: Pointer,
        ): Pointer?

        fun objc_msgSend(
            receiver: Pointer,
            selector: Pointer,
            arg1: Pointer,
        ): Pointer?
    }

    /**
     * Separate JNA mapping whose [objc_msgSend] returns [Byte] so we can
     * read the BOOL result of `+[NSThread isMainThread]` without conflicting
     * with the [Pointer]-returning overloads in [ObjC].
     */
    @Suppress("ktlint:standard:function-naming")
    private interface ObjCBool : Library {
        fun objc_getClass(name: String): Pointer?

        fun sel_registerName(name: String): Pointer?

        fun objc_msgSend(
            receiver: Pointer,
            selector: Pointer,
        ): Byte
    }

    private val objc: ObjC? by lazy {
        if (!isMacOs) return@lazy null
        runCatching { Native.load("objc", ObjC::class.java) }
            .onFailure { logger.warn("failed to load libobjc: {}", it.message) }
            .getOrNull()
    }

    /** Lazily resolved selector for `+[NSThread isMainThread]`. */
    private val isMainThreadCheck: (() -> Boolean)? by lazy {
        if (!isMacOs) return@lazy null
        runCatching {
            val lib = Native.load("objc", ObjCBool::class.java)
            val nsThreadClass = lib.objc_getClass("NSThread") ?: return@lazy null
            val isMainSel = lib.sel_registerName("isMainThread") ?: return@lazy null
            // Return a lambda capturing the resolved class/selector so we
            // don't re-resolve on every drag.
            val check: () -> Boolean = { lib.objc_msgSend(nsThreadClass, isMainSel) != 0.toByte() }
            check
        }.getOrNull()
    }

    private data class Bindings(
        val sharedApp: Pointer,
        val currentEventSel: Pointer,
        val keyWindowSel: Pointer,
        val performDragSel: Pointer,
    )

    private val bindings: Bindings? by lazy {
        val obj = objc ?: return@lazy null
        try {
            val nsAppClass = obj.objc_getClass("NSApplication") ?: return@lazy null
            val sharedAppSel = obj.sel_registerName("sharedApplication") ?: return@lazy null
            val sharedApp = obj.objc_msgSend(nsAppClass, sharedAppSel) ?: return@lazy null
            val currentEventSel = obj.sel_registerName("currentEvent") ?: return@lazy null
            val keyWindowSel = obj.sel_registerName("keyWindow") ?: return@lazy null
            val performDragSel = obj.sel_registerName("performWindowDragWithEvent:") ?: return@lazy null
            Bindings(
                sharedApp = sharedApp,
                currentEventSel = currentEventSel,
                keyWindowSel = keyWindowSel,
                performDragSel = performDragSel,
            )
        } catch (t: Throwable) {
            logger.warn("failed to resolve AppKit bindings: {}", t.message)
            null
        }
    }

    fun startDrag(): Boolean {
        val obj = objc ?: return false
        val b = bindings ?: return false

        // Cheap main-thread guard — see the JBR thread contract in the class
        // KDoc above. In normal operation this check always passes; a warning
        // here means a JBR version has decoupled the EDT from the AppKit main
        // thread and the performWindowDragWithEvent: call is no longer safe.
        val onMain = isMainThreadCheck?.invoke() ?: true
        if (!onMain) {
            logger.warn(
                "startDrag() called off the AppKit main thread — skipping " +
                    "performWindowDragWithEvent: to avoid undefined AppKit behaviour. " +
                    "The JBR EDT/AppKit main-thread contract may have changed.",
            )
            return false
        }

        return try {
            val currentEvent = obj.objc_msgSend(b.sharedApp, b.currentEventSel) ?: return false
            val keyWindow = obj.objc_msgSend(b.sharedApp, b.keyWindowSel) ?: return false
            obj.objc_msgSend(keyWindow, b.performDragSel, currentEvent)
            true
        } catch (t: Throwable) {
            logger.warn("performWindowDragWithEvent: threw: {}", t.message)
            false
        }
    }
}
