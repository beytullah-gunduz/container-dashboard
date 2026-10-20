package com.containerdashboard

import com.containerdashboard.terminal.closeTerminalWidget
import com.containerdashboard.terminal.createTerminalWidget
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Each Console session builds a JediTermWidget with its own executors. Compose disposing the
 * SwingPanel does not stop them; the widget has to be closed (review-2026-10 G3).
 */
class TerminalWidgetCloseTest {
    private val stub =
        object : TtyConnector {
            override fun read(
                buf: CharArray,
                offset: Int,
                length: Int,
            ): Int = -1

            override fun write(bytes: ByteArray) {}

            override fun write(string: String) {}

            override fun isConnected(): Boolean = false

            override fun resize(termSize: TermSize) {}

            override fun waitFor(): Int = 0

            override fun ready(): Boolean = false

            override fun getName(): String = "test"

            override fun close() {}
        }

    @Test
    fun `closing the widget stops its executors`() {
        var widget: JediTermWidget? = null
        SwingUtilities.invokeAndWait { widget = createTerminalWidget(stub, darkTheme = true) }
        val created = widget!!
        val executor = created.executorServiceManager.singleThreadScheduledExecutor

        closeTerminalWidget(created)
        SwingUtilities.invokeAndWait {} // let the queued close run

        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "the fast-job executor must terminate")
    }
}
