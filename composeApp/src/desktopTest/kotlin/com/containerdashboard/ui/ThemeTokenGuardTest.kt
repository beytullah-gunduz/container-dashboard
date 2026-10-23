package com.containerdashboard.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the theme tokens: no main source file may hardcode a corner shape, a circle, an
 * [com.containerdashboard.ui.theme.AppColors] constant or the platform monospace family. They must
 * come from the tokens (`N.dp.appCorner`, `appCorners(...)`, `appRoundShape`, `appStrokeCap`,
 * `AppTheme.extended.*`, `appMonoFamily()`), which follow the active style.
 *
 * `Theme.kt` and `DesignTokens.kt` are the token homes. `ThemePreviewCard.kt` draws mockups of the
 * themes with literal shapes and colours on purpose, so the thumbnails stay truthful.
 *
 * The scan is a masking text scanner ([ThemeTokenScanner]), not a parser: strings, char literals
 * and comments are blanked out first so a mention in a comment never fires, and import lines are
 * skipped. The canary tests pin the scanner's behaviour on synthetic sources, so a broken scanner
 * cannot make the repo scan pass vacuously. Read-only: it never writes anything.
 */
class ThemeTokenGuardTest {
    @Test
    fun `no hardcoded shapes colours or monospace outside the token files`() {
        val roots = listOf(sourceRoot("commonMain"), sourceRoot("desktopMain"))
        roots.forEach { assertTrue(it.isDirectory, "main source root not found: ${it.path}") }
        val files =
            roots.flatMap { root ->
                root
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .map { root to it }
                    .toList()
            }
        assertTrue(files.size >= 100, "suspiciously few main sources (${files.size})")

        val violations =
            files
                .map { (root, file) -> file to file.relativeTo(root).invariantSeparatorsPath }
                .filter { (_, relative) -> relative !in EXEMPT_FILES }
                .flatMap { (file, relative) -> ThemeTokenScanner.scan(relative, file.readText()) }
                .sortedWith(compareBy({ it.file }, { it.line }))

        if (violations.isNotEmpty()) {
            fail(
                buildString {
                    append(HEADER)
                    violations.forEach { append('\n').append("${it.file}:${it.line}: ${it.pattern}") }
                },
            )
        }
    }

    @Test
    fun `each pattern fires on its own line`() {
        val source =
            src(
                "val a = RoundedCornerShape(4.dp)",
                "val b = CircleShape",
                "val c = AppColors.Running",
                "val d = FontFamily.Monospace",
            )
        assertEquals(
            listOf(
                1 to "RoundedCornerShape(",
                2 to "CircleShape",
                3 to "AppColors.",
                4 to "FontFamily.Monospace",
            ),
            hits(source),
        )
    }

    @Test
    fun `comments strings and imports do not fire`() {
        val source =
            src(
                "// RoundedCornerShape(4.dp) CircleShape AppColors.Running FontFamily.Monospace",
                "/* RoundedCornerShape(4.dp)",
                "   CircleShape */",
                "val a = \"RoundedCornerShape( CircleShape AppColors.Running FontFamily.Monospace\"",
                "val b = \"\\\"RoundedCornerShape(\\\"\"",
                "val c = ${TQ}CircleShape",
                "AppColors.Running$TQ",
                "val d = '\"'",
                "import androidx.compose.foundation.shape.RoundedCornerShape",
                "import androidx.compose.foundation.shape.CircleShape",
                "import com.containerdashboard.ui.theme.AppColors",
                "import androidx.compose.ui.text.font.FontFamily.Monospace",
            )
        assertEquals(emptyList(), hits(source))
    }

    @Test
    fun `code after a block comment or a raw string still fires`() {
        val source =
            src(
                "/* RoundedCornerShape(4.dp)",
                "   CircleShape */",
                "val a = Modifier.clip(CircleShape)",
                "val b = ${TQ}x",
                "AppColors.Running$TQ",
                "val c = AppColors.Stopped",
                "val d = '\"'",
                "val e = FontFamily.Monospace",
            )
        assertEquals(
            listOf(3 to "CircleShape", 6 to "AppColors.", 8 to "FontFamily.Monospace"),
            hits(source),
        )
    }

    @Test
    fun `the token spellings do not fire`() {
        val source =
            src(
                "val a = Radius.md.appCorner",
                "val b = appRoundShape",
                "val c = appStrokeCap",
                "val d = AppTheme.extended.running",
                "val e = appMonoFamily()",
                "val f = LocalAppColors.current",
            )
        assertEquals(emptyList(), hits(source))
    }

    private fun hits(source: String): List<Pair<Int, String>> = ThemeTokenScanner.scan("Sample.kt", source).map { it.line to it.pattern }

    private fun src(vararg lines: String): String = lines.joinToString("\n")

    /** Gradle runs tests from the composeApp project directory; an IDE run starts at the repo root. */
    private fun sourceRoot(set: String): File {
        val fromProject = File("src/$set/kotlin")
        return if (fromProject.isDirectory) fromProject else File("composeApp/src/$set/kotlin")
    }

    private companion object {
        val EXEMPT_FILES =
            setOf(
                "com/containerdashboard/ui/theme/Theme.kt",
                "com/containerdashboard/ui/theme/DesignTokens.kt",
                "com/containerdashboard/ui/screens/ThemePreviewCard.kt",
            )
        const val TQ = "\"\"\""
        const val HEADER =
            "Hardcoded shapes, colours or monospace bypass the theme style. " +
                "Use the tokens from ui/theme (appCorner, appCorners, appRoundShape, appStrokeCap, " +
                "AppTheme.extended, appMonoFamily)."
    }
}

/**
 * The text scanner behind [ThemeTokenGuardTest]. It first masks strings, char literals and comments
 * to spaces (offsets and newlines preserved), then runs the patterns on the masked text. It is a
 * scanner, not a parser: it must never throw, whatever the input.
 */
internal object ThemeTokenScanner {
    data class Violation(
        val file: String,
        val line: Int,
        val pattern: String,
    )

    private class Pattern(
        val label: String,
        val regex: Regex,
    )

    private val patterns =
        listOf(
            Pattern("RoundedCornerShape(", Regex("""RoundedCornerShape\(""")),
            Pattern("CircleShape", Regex("""\bCircleShape\b""")),
            Pattern("AppColors.", Regex("""\bAppColors\.""")),
            Pattern("FontFamily.Monospace", Regex("""FontFamily\.Monospace""")),
        )

    private enum class State { CODE, STRING, RAW, CHAR, LINE_COMMENT, BLOCK_COMMENT }

    fun scan(
        file: String,
        source: String,
    ): List<Violation> {
        val masked = mask(source)
        val maskedLines = masked.split('\n')
        val skipped = maskedLines.map { it.trim().startsWith("import ") }
        val lineStarts = lineStarts(masked)

        fun lineOf(offset: Int): Int {
            val found = lineStarts.binarySearch(offset)
            return (if (found >= 0) found else -found - 2) + 1
        }

        val violations = mutableListOf<Violation>()
        for (pattern in patterns) {
            for (match in pattern.regex.findAll(masked)) {
                val line = lineOf(match.range.first)
                if (!skipped[line - 1]) violations += Violation(file, line, pattern.label)
            }
        }
        return violations.sortedBy { it.line }
    }

    /**
     * A copy of [source] of identical length with every string, char literal and comment character
     * replaced by a space. Newlines are kept. A plain string or char literal also ends at its line's
     * end, and template expressions are not parsed, so a stray quote can never swallow the rest of
     * the file.
     */
    fun mask(source: String): String {
        val n = source.length
        val out = CharArray(n)
        var state = State.CODE
        var i = 0

        fun blank(index: Int) {
            val c = source[index]
            out[index] = if (c == '\n' || c == '\r') c else ' '
        }

        while (i < n) {
            val c = source[i]
            when (state) {
                State.CODE -> {
                    when {
                        source.startsWith(TRIPLE_QUOTE, i) -> {
                            repeat(3) { blank(i + it) }
                            i += 3
                            state = State.RAW
                        }

                        c == '"' -> {
                            blank(i)
                            i++
                            state = State.STRING
                        }

                        c == '\'' -> {
                            blank(i)
                            i++
                            state = State.CHAR
                        }

                        source.startsWith("//", i) -> {
                            repeat(2) { blank(i + it) }
                            i += 2
                            state = State.LINE_COMMENT
                        }

                        source.startsWith("/*", i) -> {
                            repeat(2) { blank(i + it) }
                            i += 2
                            state = State.BLOCK_COMMENT
                        }

                        else -> {
                            out[i] = c
                            i++
                        }
                    }
                }

                State.STRING, State.CHAR -> {
                    val quote = if (state == State.STRING) '"' else '\''
                    when {
                        c == '\n' -> {
                            out[i] = c
                            i++
                            state = State.CODE
                        }

                        c == '\\' && i + 1 < n && source[i + 1] != '\n' -> {
                            blank(i)
                            blank(i + 1)
                            i += 2
                        }

                        c == quote -> {
                            blank(i)
                            i++
                            state = State.CODE
                        }

                        else -> {
                            blank(i)
                            i++
                        }
                    }
                }

                State.RAW -> {
                    if (source.startsWith(TRIPLE_QUOTE, i)) {
                        while (i < n && source[i] == '"') {
                            blank(i)
                            i++
                        }
                        state = State.CODE
                    } else {
                        blank(i)
                        i++
                    }
                }

                State.LINE_COMMENT -> {
                    if (c == '\n') state = State.CODE
                    blank(i)
                    i++
                }

                State.BLOCK_COMMENT -> {
                    if (source.startsWith("*/", i)) {
                        repeat(2) { blank(i + it) }
                        i += 2
                        state = State.CODE
                    } else {
                        blank(i)
                        i++
                    }
                }
            }
        }
        return String(out)
    }

    private const val TRIPLE_QUOTE = "\"\"\""

    private fun lineStarts(text: String): IntArray {
        val starts = mutableListOf(0)
        text.forEachIndexed { index, c -> if (c == '\n') starts += index + 1 }
        return starts.toIntArray()
    }
}
