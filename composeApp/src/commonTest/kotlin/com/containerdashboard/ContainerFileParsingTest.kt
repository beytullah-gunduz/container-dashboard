package com.containerdashboard

import com.containerdashboard.data.models.FileType
import com.containerdashboard.data.util.joinPath
import com.containerdashboard.data.util.looksBinary
import com.containerdashboard.data.util.normalizePath
import com.containerdashboard.data.util.parseLsOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContainerFileParsingTest {
    // ---------------------------------------------------------------------
    // parseLsOutput
    // ---------------------------------------------------------------------

    private val gnuListing =
        """
        total 68
        drwxr-xr-x  19 root root  4096 Jun  8 10:00 .
        drwxr-xr-x  19 root root  4096 Jun  8 10:00 ..
        lrwxrwxrwx   1 root root     7 Apr 22  2024 bin -> usr/bin
        drwxr-xr-x   2 root root  4096 Apr 22  2024 boot
        -rw-r--r--   1 root root   220 Jan  6  2022 hello world.txt
        crw-rw-rw-   1 root root  1,   3 Jun  8 09:00 null
        """.trimIndent()

    @Test
    fun `parses GNU ls output, skipping total and dot entries`() {
        val entries = parseLsOutput(gnuListing, "/")
        // total, "." and ".." are dropped.
        assertEquals(listOf("bin", "boot", "hello world.txt", "null"), entries.map { it.name })
    }

    @Test
    fun `classifies entry types from the permission char`() {
        val byName = parseLsOutput(gnuListing, "/").associateBy { it.name }
        assertEquals(FileType.SYMLINK, byName.getValue("bin").type)
        assertEquals(FileType.DIRECTORY, byName.getValue("boot").type)
        assertEquals(FileType.FILE, byName.getValue("hello world.txt").type)
        assertEquals(FileType.OTHER, byName.getValue("null").type) // character device
    }

    @Test
    fun `extracts symlink target and strips it from the name`() {
        val bin = parseLsOutput(gnuListing, "/").first { it.name == "bin" }
        assertEquals("usr/bin", bin.symlinkTarget)
        assertEquals("/bin", bin.path)
    }

    @Test
    fun `keeps spaces in file names and parses size`() {
        val file = parseLsOutput(gnuListing, "/").first { it.name == "hello world.txt" }
        assertEquals(220L, file.sizeBytes)
        assertEquals("/hello world.txt", file.path)
        assertNull(file.symlinkTarget)
    }

    @Test
    fun `device-node size falls back to zero`() {
        val dev = parseLsOutput(gnuListing, "/").first { it.name == "null" }
        assertEquals(0L, dev.sizeBytes)
    }

    @Test
    fun `parses BusyBox ls output with numeric owners and a non-root base path`() {
        val busybox =
            """
            total 12
            drwxr-xr-x    2 root     root          4096 Jun  8 10:00 .
            drwxr-xr-x    1 root     root          4096 Jun  8 10:00 ..
            -rwxr-xr-x    1 root     root           512 Jun  8 10:00 run.sh
            drwxr-xr-x    2 1000     1000          4096 Jun  8 10:00 data
            """.trimIndent()
        val byName = parseLsOutput(busybox, "/app").associateBy { it.name }
        assertEquals(listOf("run.sh", "data"), byName.keys.toList())
        assertEquals(FileType.FILE, byName.getValue("run.sh").type)
        assertEquals(512L, byName.getValue("run.sh").sizeBytes)
        assertEquals("/app/run.sh", byName.getValue("run.sh").path)
        assertEquals(FileType.DIRECTORY, byName.getValue("data").type)
        assertEquals("/app/data", byName.getValue("data").path)
    }

    @Test
    fun `skips unparseable lines instead of failing`() {
        val garbage = "this is not an ls line\n??? broken\n"
        assertEquals(emptyList(), parseLsOutput(garbage, "/"))
    }

    // ---------------------------------------------------------------------
    // normalizePath / joinPath
    // ---------------------------------------------------------------------

    @Test
    fun `normalizePath resolves dot-dot and collapses slashes`() {
        assertEquals("/a/c", normalizePath("/a/b/../c"))
        assertEquals("/a/b", normalizePath("/a//b"))
        assertEquals("/a/b", normalizePath("/a/./b"))
        assertEquals("/a/b", normalizePath("/a/b/"))
    }

    @Test
    fun `normalizePath clamps at root and defaults blank to root`() {
        assertEquals("/", normalizePath("/.."))
        assertEquals("/", normalizePath("///"))
        assertEquals("/", normalizePath(""))
        assertEquals("/", normalizePath("/", ".."))
    }

    @Test
    fun `normalizePath appends an optional name`() {
        assertEquals("/a", normalizePath("/a/b", ".."))
        assertEquals("/a/b/c", normalizePath("/a/b", "c"))
    }

    @Test
    fun `joinPath builds normalized child paths`() {
        assertEquals("/etc", joinPath("/", "etc"))
        assertEquals("/a/b", joinPath("/a", "b"))
        assertEquals("/a/b", joinPath("/a/", "b"))
    }

    // ---------------------------------------------------------------------
    // looksBinary
    // ---------------------------------------------------------------------

    @Test
    fun `looksBinary detects NUL bytes`() {
        assertTrue(looksBinary(byteArrayOf(0x68, 0x00, 0x69)))
    }

    @Test
    fun `looksBinary treats plain text and empty input as non-binary`() {
        assertFalse(looksBinary(ByteArray(0)))
        assertFalse(looksBinary("hello world\n".encodeToByteArray()))
        assertFalse(looksBinary("café ☕".encodeToByteArray())) // high UTF-8 bytes are fine
    }

    @Test
    fun `looksBinary flags a high ratio of control bytes`() {
        assertTrue(looksBinary(ByteArray(100) { 0x01 }))
    }
}
