package com.containerdashboard

import com.containerdashboard.ui.util.formatBytes
import com.containerdashboard.ui.util.formatBytesPerSecond
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [formatBytes] / [formatBytesPerSecond] tier selection and boundary behavior (S8.5).
 *
 * The implementation renders fractions with `"%.1f".format(...)` / `"%.2f".format(...)`,
 * which is locale-dependent on the JVM (e.g. `1,0` under tr_TR). Expected values here are
 * built through the same format calls so the tests pin tier/suffix/value without also
 * pinning the machine's decimal separator.
 */
class FormatUtilsTest {
    private fun f1(v: Double) = "%.1f".format(v)

    private fun f2(v: Double) = "%.2f".format(v)

    private val kib = 1024L
    private val mib = 1024L * 1024
    private val gib = 1024L * 1024 * 1024

    // ------------------------------------------------------------------
    // formatBytes — bytes tier
    // ------------------------------------------------------------------

    @Test
    fun `zero bytes stays in the bytes tier`() {
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun `1023 bytes is the last value in the bytes tier`() {
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `negative values render verbatim in the bytes tier`() {
        // Any negative value is < 1024, so it can never reach the KB+ tiers.
        assertEquals("-1 B", formatBytes(-1))
        assertEquals("-123456789 B", formatBytes(-123_456_789))
    }

    // ------------------------------------------------------------------
    // formatBytes — KB tier
    // ------------------------------------------------------------------

    @Test
    fun `1024 bytes is exactly one KB`() {
        assertEquals("${f1(1.0)} KB", formatBytes(kib))
    }

    @Test
    fun `KB values keep one fractional digit`() {
        assertEquals("${f1(1.5)} KB", formatBytes(1536))
    }

    @Test
    fun `one byte below a MiB rounds up to 1024 KB but keeps the KB suffix`() {
        // 1048575 / 1024 = 1023.999..., which %.1f rounds to 1024.0 — the tier check
        // happens before rounding, so the rendered value overshoots the tier ceiling.
        assertEquals("${f1(1048575 / 1024.0)} KB", formatBytes(mib - 1))
    }

    // ------------------------------------------------------------------
    // formatBytes — MB tier
    // ------------------------------------------------------------------

    @Test
    fun `one MiB is exactly one MB`() {
        assertEquals("${f1(1.0)} MB", formatBytes(mib))
    }

    @Test
    fun `MB values keep one fractional digit`() {
        assertEquals("${f1(5.5)} MB", formatBytes(mib * 5 + mib / 2))
    }

    @Test
    fun `one byte below a GiB stays in the MB tier`() {
        assertEquals("${f1((gib - 1) / 1024.0 / 1024.0)} MB", formatBytes(gib - 1))
    }

    // ------------------------------------------------------------------
    // formatBytes — GB tier (top tier, two fractional digits)
    // ------------------------------------------------------------------

    @Test
    fun `one GiB is exactly one GB with two fractional digits`() {
        assertEquals("${f2(1.0)} GB", formatBytes(gib))
    }

    @Test
    fun `values above 1024 GB stay in the GB tier - there is no TB tier`() {
        assertEquals("${f2(1536.0)} GB", formatBytes(gib * 1536))
    }

    // ------------------------------------------------------------------
    // formatBytesPerSecond — same tiers with a per-second suffix
    // ------------------------------------------------------------------

    @Test
    fun `rates below 1024 render as bytes per second`() {
        assertEquals("0 B/s", formatBytesPerSecond(0))
        assertEquals("1023 B/s", formatBytesPerSecond(1023))
        assertEquals("-1 B/s", formatBytesPerSecond(-1))
    }

    @Test
    fun `rate tier boundaries match formatBytes`() {
        assertEquals("${f1(1.0)} KB/s", formatBytesPerSecond(kib))
        assertEquals("${f1(1.0)} MB/s", formatBytesPerSecond(mib))
        assertEquals("${f2(1.0)} GB/s", formatBytesPerSecond(gib))
    }

    @Test
    fun `rate fractions match the byte formatter`() {
        assertEquals("${f1(1.5)} KB/s", formatBytesPerSecond(1536))
        assertEquals("${f1(2.5)} MB/s", formatBytesPerSecond(mib * 2 + mib / 2))
        assertEquals("${f2(2.5)} GB/s", formatBytesPerSecond(gib * 2 + gib / 2))
    }

    @Test
    fun `rate one byte below a tier boundary stays in the lower tier`() {
        assertEquals("1023 B/s", formatBytesPerSecond(kib - 1))
        assertEquals("${f1((mib - 1) / 1024.0)} KB/s", formatBytesPerSecond(mib - 1))
        assertEquals("${f1((gib - 1) / 1024.0 / 1024.0)} MB/s", formatBytesPerSecond(gib - 1))
    }
}
