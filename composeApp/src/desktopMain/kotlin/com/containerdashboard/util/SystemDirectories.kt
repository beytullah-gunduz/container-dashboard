package com.containerdashboard.util

import kotlinx.io.files.Path
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private val logger = LoggerFactory.getLogger("com.containerdashboard.util.SystemDirectories")

object SystemDirectories {
    val applicationDirectory: Path by lazy {
        val dir = resolveApplicationDirectory()
        maybeMigrate(dir)
        dir
    }

    private fun resolveApplicationDirectory(): Path {
        val appData = System.getenv("APPDATA")
        if (appData != null) {
            // Windows — use %APPDATA%\container-dashboard
            return Path("$appData/container-dashboard")
        }
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        return if (osName.contains("mac")) {
            // macOS — use ~/Library/Application Support/container-dashboard
            val home = System.getProperty("user.home")
            Path("$home/Library/Application Support/container-dashboard")
        } else {
            // Linux and other UNIX — keep the original dotfolder
            val home = System.getProperty("user.home")
            Path("$home/.container-dashboard")
        }
    }

    /**
     * One-time migration for macOS users who have an existing
     * `~/.container-dashboard` directory from a pre-S6.4 build.
     *
     * If the legacy path exists and the new macOS path does not, we attempt
     * a [Files.move] of the whole directory. On failure (e.g. cross-device
     * move) we fall back gracefully: log a warning and leave both paths
     * intact so the user is not silently reset to defaults.
     */
    private fun maybeMigrate(newDir: Path) {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        if (!osName.contains("mac")) return

        val home = System.getProperty("user.home")
        val legacy = Paths.get("$home/.container-dashboard")
        val target = Paths.get(newDir.toString())

        if (!Files.exists(legacy)) return
        if (Files.exists(target)) return

        // Ensure the parent of the new location exists
        target.parent?.let { Files.createDirectories(it) }

        try {
            Files.move(legacy, target, StandardCopyOption.ATOMIC_MOVE)
            logger.info(
                "Migrated app data from {} to {}",
                legacy,
                target,
            )
        } catch (e: Exception) {
            // ATOMIC_MOVE can fail across filesystems; retry without it
            try {
                Files.move(legacy, target)
                logger.info(
                    "Migrated app data from {} to {} (non-atomic)",
                    legacy,
                    target,
                )
            } catch (e2: Exception) {
                logger.warn(
                    "Could not migrate app data from {} to {} — existing settings at the old path will still be used. " +
                        "Error: {}",
                    legacy,
                    target,
                    e2.message,
                )
            }
        }
    }
}
