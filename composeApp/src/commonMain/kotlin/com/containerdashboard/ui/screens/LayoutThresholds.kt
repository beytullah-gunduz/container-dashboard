package com.containerdashboard.ui.screens

import androidx.compose.ui.unit.dp

/**
 * Shared breakpoints that govern when resource-list screens switch
 * between "compact" and "expanded" layouts. Keeping them in one
 * place prevents the values from drifting as screens are tweaked.
 */
internal val COMPACT_THRESHOLD = 700.dp

/**
 * Below this width, toolbar buttons collapse to icon-only. Currently
 * used by `ContainersScreen`.
 */
internal val COMPACT_BUTTONS_THRESHOLD = 900.dp

/**
 * Below this width, the monitoring per-container table collapses to
 * a two-row-per-container layout so disk/network columns stay legible.
 */
internal val NARROW_TABLE_THRESHOLD = 900.dp
