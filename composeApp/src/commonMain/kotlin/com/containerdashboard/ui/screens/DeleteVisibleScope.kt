package com.containerdashboard.ui.screens

/**
 * Bulk-delete scope helpers for the Containers screen (UX audit U1.4). The
 * destructive menu item acts on the containers currently shown (search +
 * filter), and the confirmation dialog demands the count be typed back once
 * the set is large enough that a reflex click would be costly.
 */
internal const val TYPED_CONFIRM_THRESHOLD = 10

/** Menu label for deleting the [visible] containers out of [total]. */
internal fun deleteVisibleLabel(
    visible: Int,
    total: Int,
): String {
    val noun = if (visible == 1) "container" else "containers"
    return when {
        visible == 0 -> "Delete containers…"
        visible == total && visible == 1 -> "Delete the only container…"
        visible == total -> "Delete all $visible $noun…"
        else -> "Delete $visible matching $noun…"
    }
}

/** Dialog title for deleting [count] containers; [isSubset] when a search/filter narrowed the set. */
internal fun deleteVisibleTitle(
    count: Int,
    isSubset: Boolean,
): String {
    val noun = if (count == 1) "container" else "containers"
    return when {
        isSubset -> "Delete $count matching $noun?"
        count == 1 -> "Delete the only container?"
        else -> "Delete all $count $noun?"
    }
}

/** True when the dialog must require the count to be typed back. */
internal fun requiresTypedConfirmation(count: Int): Boolean = count > TYPED_CONFIRM_THRESHOLD

/** True when [typed] confirms deleting exactly [count] containers. */
internal fun typedCountMatches(
    typed: String,
    count: Int,
): Boolean = typed.trim() == count.toString()
