package com.containerdashboard.data.util

/**
 * Normalize an absolute container path:
 *  - if [name] is non-null, first append it to [path],
 *  - collapse duplicate slashes,
 *  - resolve `.` (drop) and `..` (pop one segment, never above root),
 *  - always absolute (leading `/`); empty / blank input becomes `/`.
 *
 * Trailing slashes are dropped except for root.
 */
fun normalizePath(
    path: String,
    name: String? = null,
): String {
    val raw =
        when {
            name == null -> path
            path.endsWith("/") -> "$path$name"
            else -> "$path/$name"
        }
    if (raw.isBlank()) return "/"
    val segments = ArrayDeque<String>()
    for (seg in raw.split('/')) {
        when (seg) {
            "", "." -> Unit // skip empties (collapses //) and "."
            ".." -> if (segments.isNotEmpty()) segments.removeLast() // pop, clamp at root
            else -> segments.addLast(seg)
        }
    }
    return if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
}

/** Join a base directory and a child name into a normalized absolute path. */
fun joinPath(
    basePath: String,
    name: String,
): String = normalizePath(if (basePath.endsWith("/")) "$basePath$name" else "$basePath/$name")
