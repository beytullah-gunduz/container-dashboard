package com.containerdashboard.ui.chrome

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import org.slf4j.LoggerFactory

/**
 * Initiates a native macOS window drag by forwarding the current NSEvent to
 * `-[NSWindow performWindowDragWithEvent:]`.
 *
 * Why this exists: the app draws its own title bar under macOS's transparent
 * one (see [TransparentTitleBarOnMac]), so presses on it reach Compose rather
 * than AppKit's title bar. Compose Desktop's `WindowDraggableArea` would then
 * move the window entirely in Kotlin: it reads AWT mouse deltas and calls
 * `frame.setLocation(x, y)`. On macOS multi-monitor setups, AWT's screen
 * coordinate mapping disagrees with AppKit's unified coordinate space
 * whenever the cursor crosses into a secondary display, so the window gets
 * clamped at the primary display's edge instead of following the cursor
 * across. Delegating to `performWindowDragWithEvent:` hands the drag to
 * AppKit's window server and multi-monitor just works.
 *
 * Threads: Compose pointer input runs on the AWT event thread (the EDT), which
 * is NOT the AppKit main thread: `+[NSThread isMainThread]` answers NO there.
 * An earlier guard skipped the drag in that case, which left the title bar
 * unable to move the window at all. [startDrag] calls
 * `performWindowDragWithEvent:` from the EDT on purpose: it reads
 * `[NSApp currentEvent]`, which only points at the mouse-down while AppKit is
 * still dispatching it. An async hop to a later main run-loop turn would lose
 * that event, and a synchronous hop would block the EDT on the main thread,
 * which can deadlock. It fires only during a user drag, never at startup.
 * Revisit if it ever hangs.
 *
 * No-op on non-macOS platforms ([startDrag] returns `false`).
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
    }

    private val objc: ObjC? by lazy {
        if (!isMacOs) return@lazy null
        runCatching { Native.load("objc", ObjC::class.java) }
            .onFailure { logger.warn("failed to load libobjc: {}", it.message) }
            .getOrNull()
    }

    private val msgSend: Function? by lazy {
        if (!isMacOs) return@lazy null
        runCatching { NativeLibrary.getInstance("objc").getFunction("objc_msgSend") }.getOrNull()
    }

    private val nsApplication: Pointer? by lazy { objc?.objc_getClass("NSApplication") }

    /**
     * Hands the title-bar drag to AppKit's `-[NSWindow performWindowDragWithEvent:]`
     * so the window server drives it. Returns true if AppKit took over.
     * Runs on the EDT on purpose (see the class KDoc).
     */
    fun startDrag(): Boolean {
        val o = objc ?: return false
        val send = msgSend ?: return false
        val nsApp = nsApplication ?: return false
        return runCatching {
            val sharedSel = o.sel_registerName("sharedApplication") ?: return@runCatching false
            val currentEventSel = o.sel_registerName("currentEvent") ?: return@runCatching false
            val keyWindowSel = o.sel_registerName("keyWindow") ?: return@runCatching false
            val performDragSel = o.sel_registerName("performWindowDragWithEvent:") ?: return@runCatching false

            val sharedApp = send.invokePointer(arrayOf(nsApp, sharedSel)) ?: return@runCatching false
            val currentEvent = send.invokePointer(arrayOf(sharedApp, currentEventSel)) ?: return@runCatching false
            val keyWindow = send.invokePointer(arrayOf(sharedApp, keyWindowSel)) ?: return@runCatching false

            send.invokeVoid(arrayOf(keyWindow, performDragSel, currentEvent))
            true
        }.onFailure { logger.warn("performWindowDragWithEvent: threw: {}", it.message) }
            .getOrDefault(false)
    }
}
