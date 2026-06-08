package com.containerdashboard.data.util

import com.containerdashboard.data.models.ContainerFileEntry
import com.containerdashboard.data.models.FileType

// One `ls -la` row: perms links owner group size <date(3 tokens)> name
//   group 1 = perms (+ optional ACL marker)   group 5 = size (or "major, minor" for devices)
//   group 6 = name (may contain spaces, or "link -> target" for symlinks)
// The short 3-token date form is emitted by both GNU coreutils and BusyBox `ls -la`
// (do NOT add --full-time: that is GNU-only and changes the date to 2 tokens).
private val LS_LINE =
    Regex(
        "^([bcdlps\\-][rwxsStT\\-]{9}[.+]?)\\s+" + // perms
            "\\d+\\s+" + // hardlink count
            "\\S+\\s+\\S+\\s+" + // owner, group
            "(\\d+(?:,\\s*\\d+)?)\\s+" + // size (plain, or "maj, min" for device nodes)
            "(?:\\S+\\s+){2}\\S+\\s+" + // date: exactly 3 whitespace-separated tokens
            "(.+)$", // name
    )

/**
 * Parse `ls -la` stdout into entries. Pure; no I/O. The `total N` header, `.`/`..`, and any
 * unparseable line (e.g. a filename containing a newline) are skipped rather than failing.
 *
 * @param basePath the directory that was listed (absolute), used to build each child's path.
 */
fun parseLsOutput(
    raw: String,
    basePath: String,
): List<ContainerFileEntry> {
    val entries = ArrayList<ContainerFileEntry>()
    for (line in raw.lineSequence()) {
        if (line.isBlank() || line.startsWith("total ")) continue
        val match = LS_LINE.matchEntire(line.trimEnd('\r')) ?: continue
        val perms = match.groupValues[1]
        val size = match.groupValues[2].toLongOrNull() ?: 0L
        var name = match.groupValues[3]

        var symlinkTarget: String? = null
        if (perms.first() == 'l') {
            val arrow = name.indexOf(" -> ")
            if (arrow >= 0) {
                symlinkTarget = name.substring(arrow + 4)
                name = name.substring(0, arrow)
            }
        }
        if (name == "." || name == "..") continue

        entries.add(
            ContainerFileEntry(
                name = name,
                path = joinPath(basePath, name),
                type = fileTypeFromPermChar(perms.first()),
                sizeBytes = size,
                permissions = perms,
                symlinkTarget = symlinkTarget,
            ),
        )
    }
    return entries
}

/** Map the leading `ls -l` type char to a [FileType]. */
fun fileTypeFromPermChar(c: Char): FileType =
    when (c) {
        'd' -> FileType.DIRECTORY
        'l' -> FileType.SYMLINK
        '-' -> FileType.FILE
        else -> FileType.OTHER // b/c (devices), p (fifo), s (socket)
    }

/**
 * Heuristic binary detection over the leading window: a NUL byte (the strongest signal, also
 * used by git) or more than 30% non-text bytes means "treat as binary".
 */
fun looksBinary(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val window = minOf(bytes.size, 8192)
    var nonText = 0
    for (i in 0 until window) {
        val b = bytes[i].toInt() and 0xFF
        if (b == 0) return true // NUL => binary
        val printable =
            b == 0x09 || b == 0x0A || b == 0x0D || // tab, LF, CR
                b in 0x20..0x7E || // printable ASCII
                b >= 0x80 // UTF-8 lead/continuation bytes
        if (!printable) nonText++
    }
    return nonText * 100 / window > 30
}
