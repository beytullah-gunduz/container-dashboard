package com.containerdashboard.data.repository

data class PruneResult(
    val deletedCount: Int,
    val reclaimedSpace: Long = 0,
)
