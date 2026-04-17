package com.containerdashboard.ui.state

import com.containerdashboard.data.models.Container

data class LogsPaneState(
    val containers: List<Container> = emptyList(),
    val logs: String = "",
    val isLoading: Boolean = false,
    val isFollowing: Boolean = false,
    val isSavingLogs: Boolean = false,
    val error: String? = null,
) {
    val container: Container? get() = containers.singleOrNull()
    val isGroupMode: Boolean get() = containers.size > 1
    val displayName: String
        get() = when {
            isGroupMode -> containers.firstOrNull()?.composeProject ?: "Group"
            containers.size == 1 -> containers.first().displayName
            else -> ""
        }
}
