package com.containerdashboard.data.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import com.containerdashboard.util.SystemDirectories
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

actual fun dataStorePreferences(
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    coroutineScope: CoroutineScope,
    migrations: List<DataMigration<Preferences>>,
): DataStore<Preferences> {
    val prefPath = SystemDirectories.applicationDirectory.toString() + "/" + PREFERENCE_DATASTORE
    // Restrict the preferences file to owner-only (rw-------) so that the engine host URL
    // (which may contain embedded credentials) is not world-readable.
    // We create the file now if absent and set POSIX permissions; on non-POSIX filesystems
    // (e.g. Windows NTFS) the call is silently skipped.
    restrictPrefsFilePermissions(prefPath)
    return createDataStoreWithDefaults(
        corruptionHandler = corruptionHandler,
        coroutineScope = coroutineScope,
        migrations = migrations,
        path = { prefPath },
    )
}

private fun restrictPrefsFilePermissions(prefPath: String) {
    try {
        val path = Paths.get(prefPath)
        path.parent?.let { Files.createDirectories(it) }
        if (!Files.exists(path)) {
            Files.createFile(path)
        }
        val ownerOnly =
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            )
        Files.setPosixFilePermissions(path, ownerOnly)
    } catch (_: UnsupportedOperationException) {
        // Non-POSIX filesystem (e.g. Windows NTFS) — permissions cannot be set this way.
    } catch (_: Exception) {
        // Best-effort; don't crash startup if the chmod fails for any other reason.
    }
}
