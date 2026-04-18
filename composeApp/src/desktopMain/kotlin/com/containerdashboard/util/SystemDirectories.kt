package com.containerdashboard.util

import kotlinx.io.files.Path

object SystemDirectories {
    val applicationDirectory: Path =
        if (System.getenv("APPDATA") != null) {
            Path(System.getenv("APPDATA") + "/container-dashboard")
        } else {
            Path(System.getProperty("user.home") + "/.container-dashboard")
        }
}
