package com.containerdashboard

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.containerdashboard.data.DockerClientRepository
import com.containerdashboard.di.AppModule

fun main() = application {
    // Initialize the Docker repository
    val dockerRepository = remember { 
        DockerClientRepository("unix:///var/run/docker.sock") 
    }
    
    DisposableEffect(Unit) {
        AppModule.initialize(dockerRepository)
        onDispose {
            dockerRepository.close()
        }
    }
    
    val windowState = rememberWindowState(
        size = DpSize(1400.dp, 900.dp),
        position = WindowPosition(Alignment.Center)
    )
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Container Dashboard",
        state = windowState
    ) {
        App()
    }
}
