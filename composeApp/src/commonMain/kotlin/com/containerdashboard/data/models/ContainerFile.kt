package com.containerdashboard.data.models

/** Classification of a directory entry, derived from the leading `ls -l` permission char. */
enum class FileType { DIRECTORY, FILE, SYMLINK, OTHER }

/**
 * A single entry from a container directory listing.
 *
 * Plain data class (not `@Serializable`): these never cross the Docker JSON wire — they are
 * parsed from `ls -la` output produced by [com.containerdashboard.data.util.parseLsOutput].
 */
data class ContainerFileEntry(
    val name: String,
    /** Absolute path inside the container. */
    val path: String,
    val type: FileType,
    /** Size in bytes; `0` for directories / device files where it is not meaningful. */
    val sizeBytes: Long,
    /** Raw permission token, e.g. `"drwxr-xr-x"`. */
    val permissions: String,
    /** Target of a symlink (the part after `->`), or `null` for non-symlinks. */
    val symlinkTarget: String? = null,
) {
    val isHidden: Boolean get() = name.startsWith(".")
}

/**
 * Payload for the inline file viewer. [text] is empty when [isBinary]; the UI then offers a
 * download instead of rendering bytes.
 */
data class ContainerFileContent(
    val text: String,
    val isBinary: Boolean,
    val truncated: Boolean,
    val totalBytesShown: Int,
)
