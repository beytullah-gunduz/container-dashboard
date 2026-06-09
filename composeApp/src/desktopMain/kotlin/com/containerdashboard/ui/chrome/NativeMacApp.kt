package com.containerdashboard.ui.chrome

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.slf4j.LoggerFactory

/**
 * Drives macOS's application-level hide / unhide via AppKit
 * (`-[NSApplication hide:]` / `-[NSApplication unhide:]`).
 *
 * Why this exists: closing the window keeps the process alive (the menu-bar
 * `Tray` holds it open), but the Dock tile is still the normal app icon.
 * macOS sends an AppKit "reopen" event when you click a running app's Dock
 * tile — and the canonical way to catch it is a
 * `com.apple.eawt.Application` `AppReOpenedListener`. That API is **gone** on
 * the JetBrains Runtime that Compose Desktop bundles (`Application` survives
 * but `AppReOpenedListener` is absent — only the internal
 * `_AppReOpenedDispatcher` remains), so there is no pure-JVM way to observe
 * the Dock click here.
 *
 * Instead of catching the reopen, we delegate hiding to AppKit itself: a
 * window hidden with `hide:` (the same path as ⌘H) is restored **natively**
 * by macOS when the user clicks the Dock tile or ⌘-Tabs back — no listener
 * required. `unhide:` is the explicit restore (used by the tray "Show"
 * action) and also reactivates the app.
 *
 * AppKit's hide/unhide must run on the AppKit main thread, but Compose click
 * handlers run on the AWT EDT (a different thread on macOS), so each call is
 * marshalled via `-[NSObject performSelectorOnMainThread:withObject:waitUntilDone:]`.
 *
 * No-op on non-macOS platforms, and on any failure to load libobjc / resolve
 * the AppKit bindings (logged, never thrown) so it can't crash the UI.
 */
object NativeMacApp {
    private val logger = LoggerFactory.getLogger(NativeMacApp::class.java)

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

        // -[NSApp performSelectorOnMainThread:withObject:waitUntilDone:]
        //   selArg  = the SEL to perform on the main thread (hide:/unhide:)
        //   objArg  = the selector's argument (nil sender)
        //   boolArg = waitUntilDone (NO)
        fun objc_msgSend(
            receiver: Pointer,
            selector: Pointer,
            selArg: Pointer,
            objArg: Pointer?,
            boolArg: Byte,
        ): Pointer?
    }

    private val objc: ObjC? by lazy {
        if (!isMacOs) return@lazy null
        runCatching { Native.load("objc", ObjC::class.java) }
            .onFailure { logger.warn("failed to load libobjc: {}", it.message) }
            .getOrNull()
    }

    private data class Bindings(
        val sharedApp: Pointer,
        val performOnMainSel: Pointer,
        val hideSel: Pointer,
        val unhideSel: Pointer,
    )

    private val bindings: Bindings? by lazy {
        val obj = objc ?: return@lazy null
        try {
            val nsAppClass = obj.objc_getClass("NSApplication") ?: return@lazy null
            val sharedAppSel = obj.sel_registerName("sharedApplication") ?: return@lazy null
            val sharedApp = obj.objc_msgSend(nsAppClass, sharedAppSel) ?: return@lazy null
            val performOnMainSel =
                obj.sel_registerName("performSelectorOnMainThread:withObject:waitUntilDone:")
                    ?: return@lazy null
            val hideSel = obj.sel_registerName("hide:") ?: return@lazy null
            val unhideSel = obj.sel_registerName("unhide:") ?: return@lazy null
            Bindings(
                sharedApp = sharedApp,
                performOnMainSel = performOnMainSel,
                hideSel = hideSel,
                unhideSel = unhideSel,
            )
        } catch (t: Throwable) {
            logger.warn("failed to resolve AppKit bindings: {}", t.message)
            null
        }
    }

    /** Hides the whole application (equivalent to ⌘H). No-op off macOS. */
    fun hide() = performOnMain(hide = true)

    /** Restores hidden windows and reactivates the app. No-op off macOS. */
    fun unhide() = performOnMain(hide = false)

    private fun performOnMain(hide: Boolean) {
        val obj = objc ?: return
        val b = bindings ?: return
        val actionSel = if (hide) b.hideSel else b.unhideSel
        try {
            // [NSApp performSelectorOnMainThread:actionSel withObject:nil waitUntilDone:NO]
            obj.objc_msgSend(b.sharedApp, b.performOnMainSel, actionSel, null, 0.toByte())
        } catch (t: Throwable) {
            logger.warn("NSApplication {} threw: {}", if (hide) "hide:" else "unhide:", t.message)
        }
    }
}
