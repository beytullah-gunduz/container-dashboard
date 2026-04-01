package com.containerdashboard.data.repository

import kotlinx.coroutines.flow.Flow

data class ExecSession(
    val execId: String,
    val containerId: String,
    val output: Flow<String>,
)
