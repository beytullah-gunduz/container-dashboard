package com.containerdashboard.ui.state

/**
 * Texts for the confirmation dialog shown before deleting the container(s)
 * open in the detail pane, and whether the dialog may be skipped when the
 * user has turned *Confirm Before Delete* off.
 */
data class PaneDeleteConfirmation(
    val title: String,
    val body: String,
    val confirmLabel: String,
    /** True when the dialog must be shown even if the preference is off. */
    val alwaysConfirm: Boolean,
)

/**
 * Builds the confirmation for the current pane target. Group mode (more than
 * one container) always confirms: [removeLogsContainer] removes every container
 * in the state, so if the toolbar's group-mode gate is ever relaxed a single
 * click must not remove a whole compose project. A single container honours
 * the *Confirm Before Delete* preference.
 */
fun paneDeleteConfirmation(state: LogsPaneState): PaneDeleteConfirmation =
    if (state.isGroupMode) {
        PaneDeleteConfirmation(
            title = "Delete all containers?",
            body =
                "This will force-stop and remove all ${state.containers.size} containers " +
                    "in \"${state.displayName}\".",
            confirmLabel = "Delete all",
            alwaysConfirm = true,
        )
    } else {
        PaneDeleteConfirmation(
            title = "Delete container?",
            body = "This will force-stop and remove \"${state.displayName}\".",
            confirmLabel = "Delete",
            alwaysConfirm = false,
        )
    }
