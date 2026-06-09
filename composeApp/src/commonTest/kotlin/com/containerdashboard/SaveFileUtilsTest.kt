package com.containerdashboard

import com.containerdashboard.ui.components.sanitizeSuggestedFileName
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveFileUtilsTest {
    @Test
    fun `plain filename passes through unchanged`() {
        assertEquals("myfile.txt", sanitizeSuggestedFileName("myfile.txt"))
    }

    @Test
    fun `unix path traversal is reduced to last component`() {
        assertEquals("evil.plist", sanitizeSuggestedFileName("../../../Library/LaunchAgents/evil.plist"))
    }

    @Test
    fun `windows path traversal is reduced to last component`() {
        assertEquals("evil.bat", sanitizeSuggestedFileName("..\\..\\Windows\\evil.bat"))
    }

    @Test
    fun `mixed separators keep last component`() {
        assertEquals("last.txt", sanitizeSuggestedFileName("/a/b\\c/last.txt"))
    }

    @Test
    fun `NUL and control characters are stripped`() {
        // Construct strings with control chars programmatically to avoid source encoding issues.
        val nul = 0.toChar()
        val bel = 7.toChar()
        val del = 127.toChar()
        assertEquals("evilfile", sanitizeSuggestedFileName("evil${nul}file"))
        assertEquals("evilfile", sanitizeSuggestedFileName("evil${bel}file"))
        assertEquals("evilfile", sanitizeSuggestedFileName("evil${del}file"))
    }

    @Test
    fun `blank result falls back to download`() {
        assertEquals("download", sanitizeSuggestedFileName(""))
        assertEquals("download", sanitizeSuggestedFileName("/"))
        assertEquals("download", sanitizeSuggestedFileName(" "))
    }
}
