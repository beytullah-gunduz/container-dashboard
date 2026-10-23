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
 * Three more rules keep Material's round defaults out of Retro. Spinners go through
 * `BusyIndicator` (only its Default delegate may call `CircularProgressIndicator`). Every M3 button
 * call passes `shape =` and every `SegmentedButtonDefaults.itemShape` passes `baseShape =`, because
 * Material resolves its default `CornerFull` shape to a circle that `MaterialTheme.shapes` never
 * reaches. The `ButtonDefaults`/`IconButtonDefaults` shape accessors are banned for the same reason.
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

    @Test
    fun `raw circular spinners fire except in BusyIndicator`() {
        assertEquals(
            listOf(1 to "CircularProgressIndicator("),
            hits("CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)"),
        )
        assertEquals(listOf(1 to "CircularProgressIndicator("), hits("androidx.compose.material3.CircularProgressIndicator()"))
        assertEquals(emptyList(), hits("BusyIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)"))
        assertEquals(emptyList(), hits("// CircularProgressIndicator( in a comment"))
        assertEquals(emptyList(), hits("import androidx.compose.material3.CircularProgressIndicator"))
        val delegate = "CircularProgressIndicator(modifier, color, strokeWidth, strokeCap = appStrokeCap)"
        assertEquals(emptyList(), ThemeTokenScanner.scan(BUSY_INDICATOR, delegate))
        assertEquals(1, ThemeTokenScanner.scan("com/containerdashboard/ui/screens/Other.kt", delegate).size)
        // The exemption is for that one pattern only: BusyIndicator.kt is still scanned for the rest.
        assertEquals(1, ThemeTokenScanner.scan(BUSY_INDICATOR, "val a = CircleShape").size)
    }

    @Test
    fun `buttons need a shape at the top level of the call`() {
        assertEquals(listOf(1 to "button-shape"), hits("TextButton(onClick = {}) { Text(\"x\") }"))
        assertEquals(emptyList(), hits("TextButton(onClick = {}, shape = appRoundShape) { Text(\"x\") }"))
        assertEquals(
            emptyList(),
            hits(src("IconButton(", " onClick = {},", " shape = appRoundShape,", ") {}")),
        )
        // A shape nested in another call or lambda is not the button's own.
        assertEquals(listOf(1 to "button-shape"), hits("Button(onClick = {}, modifier = Modifier.foo(shape = x)) {}"))
        assertEquals(listOf(1 to "button-shape"), hits("Button(onClick = { val shape = 1 }) {}"))
    }

    @Test
    fun `lookalike names declarations and defaults are not buttons`() {
        assertEquals(emptyList(), hits("fun SettingsButton(onClick: () -> Unit) {"))
        assertEquals(emptyList(), hits("SettingsButton(onClick = {})"))
        assertEquals(emptyList(), hits("private fun TextButton(x: Int) = Unit"))
    }

    @Test
    fun `a paren inside a string does not unbalance the call`() {
        assertEquals(listOf(1 to "button-shape"), hits("TextButton(onClick = { println(\")\") }) {}"))
    }

    @Test
    fun `a fully qualified button call fires`() {
        assertEquals(listOf(1 to "button-shape"), hits("androidx.compose.material3.TextButton(onClick = {}) {}"))
    }

    @Test
    fun `every button family needs a shape`() {
        val names =
            listOf(
                "Button",
                "OutlinedButton",
                "TextButton",
                "ElevatedButton",
                "FilledTonalButton",
                "IconButton",
                "FilledIconButton",
                "FilledTonalIconButton",
                "OutlinedIconButton",
                "IconToggleButton",
                "FilledIconToggleButton",
                "FilledTonalIconToggleButton",
                "OutlinedIconToggleButton",
            )
        names.forEach { name ->
            assertEquals(listOf(1 to "button-shape"), hits("$name(onClick = {}) {}"), name)
            assertEquals(emptyList(), hits("$name(onClick = {}, shape = appRoundShape) {}"), name)
        }
    }

    @Test
    fun `Material's default button shapes fire`() {
        assertEquals(listOf(1 to "m3-default-shape"), hits("val s = IconButtonDefaults.standardShape"))
        assertEquals(
            listOf(1 to "m3-default-shape"),
            hits("TextButton(onClick = {}, shape = ButtonDefaults.textShape) {}"),
        )
        assertEquals(emptyList(), hits("val p = ButtonDefaults.ContentPadding"))
        assertEquals(emptyList(), hits("val c = ButtonDefaults.outlinedButtonColors()"))
    }

    @Test
    fun `segmented items need a base shape`() {
        assertEquals(
            listOf(1 to "segmented-base-shape"),
            hits(src("shape = SegmentedButtonDefaults.itemShape(", "  index = 0,", "  count = 2,", "),")),
        )
        assertEquals(
            emptyList(),
            hits("shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2, baseShape = appRoundShape),"),
        )
    }

    @Test
    fun `an unterminated call reports and does not throw`() {
        assertEquals(listOf(1 to "button-shape"), hits("Button("))
    }

    @Test
    fun `violations come back sorted by line`() {
        val source =
            src(
                "TextButton(onClick = {}) {}",
                "val a = CircleShape",
                "val b = AppColors.Running",
            )
        val lines = ThemeTokenScanner.scan("A.kt", source).map { it.line }
        assertEquals(lines.sorted(), lines)
        assertEquals(listOf(1, 2, 3), lines)
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
        const val BUSY_INDICATOR = "com/containerdashboard/ui/components/BusyIndicator.kt"
        const val TQ = "\"\"\""
        const val HEADER =
            "Hardcoded shapes, colours or monospace bypass the theme style. " +
                "Use the tokens from ui/theme (appCorner, appCorners, appRoundShape, appStrokeCap, " +
                "AppTheme.extended, appMonoFamily). Spinners use BusyIndicator, and every M3 button " +
                "passes shape = appRoundShape (segmented items: baseShape = appRoundShape)."
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
        val exemptFiles: Set<String> = emptySet(),
    )

    /** A call to [call] whose own argument list (nested calls and lambdas excluded) must name [arg]. */
    private class CallRule(
        val label: String,
        val call: Regex,
        arg: String,
    ) {
        val argRegex = Regex("""\b$arg\s*=(?!=)""")
    }

    private val patterns =
        listOf(
            Pattern("RoundedCornerShape(", Regex("""RoundedCornerShape\(""")),
            Pattern("CircleShape", Regex("""\bCircleShape\b""")),
            Pattern("AppColors.", Regex("""\bAppColors\.""")),
            Pattern("FontFamily.Monospace", Regex("""FontFamily\.Monospace""")),
            Pattern(
                "CircularProgressIndicator(",
                Regex("""(?<!\w)CircularProgressIndicator\s*\("""),
                exemptFiles = setOf("com/containerdashboard/ui/components/BusyIndicator.kt"),
            ),
            Pattern("m3-default-shape", Regex("""\b(ButtonDefaults|IconButtonDefaults)\.\w*[sS]hapes?\b""")),
        )

    private val callRules =
        listOf(
            CallRule(
                "button-shape",
                Regex(
                    """(?<!\w)(Button|OutlinedButton|TextButton|ElevatedButton|FilledTonalButton|""" +
                        """IconButton|FilledIconButton|FilledTonalIconButton|OutlinedIconButton|""" +
                        """IconToggleButton|FilledIconToggleButton|FilledTonalIconToggleButton|OutlinedIconToggleButton)\s*\(""",
                ),
                "shape",
            ),
            CallRule("segmented-base-shape", Regex("""\bSegmentedButtonDefaults\.itemShape\s*\("""), "baseShape"),
        )

    private val funBefore = Regex("""\bfun\s+$""")

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
            if (file in pattern.exemptFiles) continue
            for (match in pattern.regex.findAll(masked)) {
                val line = lineOf(match.range.first)
                if (!skipped[line - 1]) violations += Violation(file, line, pattern.label)
            }
        }
        for (rule in callRules) {
            for (match in rule.call.findAll(masked)) {
                val line = lineOf(match.range.first)
                if (skipped[line - 1]) continue
                // A declaration (`fun TextButton(`) is not a call.
                if (funBefore.containsMatchIn(masked.substring(lineStarts[line - 1], match.range.first))) continue
                val open = match.range.last
                val close = closeParen(masked, open)
                val arguments = topLevel(if (close < 0) masked.substring(open + 1) else masked.substring(open + 1, close))
                if (close < 0 || !rule.argRegex.containsMatchIn(arguments)) {
                    violations += Violation(file, line, rule.label)
                }
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

    /** Index of the `)` matching the `(` at [open], or -1 when the call never closes. */
    private fun closeParen(
        masked: String,
        open: Int,
    ): Int {
        var depth = 0
        for (index in open until masked.length) {
            when (masked[index]) {
                '(' -> {
                    depth++
                }

                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    /** [arguments] with everything nested inside `()`, `{}` and `[]` deleted. */
    private fun topLevel(arguments: String): String {
        val text = StringBuilder()
        var depth = 0
        for (c in arguments) {
            when (c) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> if (depth > 0) depth--
                else -> if (depth == 0) text.append(c)
            }
        }
        return text.toString()
    }
}
