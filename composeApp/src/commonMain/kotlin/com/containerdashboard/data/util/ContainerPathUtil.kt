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

/**
 * Validate and normalize a container path.
 *
 * Returns [Result.failure] if [path] contains a NUL byte or any other ASCII control character
 * (code points 0x00–0x1F or 0x7F), which could be used to smuggle commands into `execCreateCmd`
 * or confuse path handling in the container runtime.
 * Otherwise returns [Result.success] with the normalized path.
 */
fun validateContainerPath(path: String): Result<String> {
    val controlChar = path.firstOrNull { it.code in 0..31 || it.code == 127 }
    if (controlChar != null) {
        return Result.failure(
            IllegalArgumentException(
                "Container path contains an illegal control character " +
                    "(code point ${controlChar.code}): refusing to proceed",
            ),
        )
    }
    return Result.success(normalizePath(path))
}
