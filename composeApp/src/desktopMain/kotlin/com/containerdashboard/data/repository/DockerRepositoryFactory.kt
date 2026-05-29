package com.containerdashboard.data.repository

actual fun createDockerRepository(dockerHost: String): DockerRepository = DesktopDockerRepository(dockerHost)
