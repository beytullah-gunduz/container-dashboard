package com.containerdashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.check
import org.jetbrains.compose.resources.painterResource

/**
 * Flat color set used to paint a [ThemeMockup] — a hand-drawn miniature of the
 * app rather than a scaled-down real composable, so it stays cheap and never
 * pulls in the live screens. Values are seeded from [AppColors] (see
 * `Theme.kt`) so the thumbnails stay truthful if the real palette is retuned.
 */
internal data class ThemePreviewColors(
    val chrome: Color,
    val sidebar: Color,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val text: Color,
)

internal val DarkPreviewColors =
    ThemePreviewColors(
        chrome = AppColors.DarkSurface,
        sidebar = AppColors.DarkSurface,
        background = AppColors.DarkBackground,
        surface = AppColors.DarkSurfaceVariant,
        accent = AppColors.AccentBlue,
        text = AppColors.TextPrimary,
    )

internal val LightPreviewColors =
    ThemePreviewColors(
        chrome = AppColors.LightSurface,
        sidebar = AppColors.LightSurface,
        background = AppColors.LightBackground,
        surface = AppColors.LightSurfaceVariant,
        accent = AppColors.AccentBlueDark,
        // Matches LightColorScheme.onBackground in Theme.kt.
        text = Color(0xFF1A1A1A),
    )

/**
 * A clickable theme thumbnail: a miniature app mockup framed by a selection
 * ring, with a radio-style indicator + [label] below. Pass [secondaryColors]
 * to render a split dark|light preview for the "Auto" (follow-system) mode.
 */
@Composable
internal fun ThemePreviewCard(
    label: String,
    selected: Boolean,
    primaryColors: ThemePreviewColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryColors: ThemePreviewColors? = null,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (selected) 2.dp else 1.dp

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(width = 160.dp, height = 110.dp)
                    .clip(RoundedCornerShape(Radius.lg))
                    .border(borderWidth, borderColor, RoundedCornerShape(Radius.lg))
                    .clickable(onClick = onClick),
        ) {
            if (secondaryColors == null) {
                ThemeMockup(primaryColors, modifier = Modifier.fillMaxSize())
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    ThemeMockup(primaryColors, modifier = Modifier.weight(1f).fillMaxHeight())
                    ThemeMockup(secondaryColors, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

/**
 * A miniature of the dashboard: a window-chrome strip (with macOS-style traffic
 * lights), a left nav rail with a brand mark and a selected item, and a content
 * area with a title, a row of stat cards, and list rows. Drawn entirely from
 * cheap [Box] primitives tinted by [colors].
 */
@Composable
private fun ThemeMockup(
    colors: ThemePreviewColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(colors.background)) {
        // Window chrome strip with macOS traffic lights.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(colors.chrome)
                    .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(3) {
                Box(
                    modifier =
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(colors.text.copy(alpha = 0.25f)),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Sidebar nav rail.
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(colors.sidebar)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Brand mark.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.accent),
                )
                // Nav items — the first reads as selected.
                repeat(4) { i ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (i == 0) {
                                        colors.accent.copy(alpha = 0.45f)
                                    } else {
                                        colors.text.copy(alpha = 0.12f)
                                    },
                                ),
                    )
                }
            }

            // Content area.
            Column(
                modifier =
                    Modifier
                        .weight(3f)
                        .fillMaxHeight()
                        .background(colors.background)
                        .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Page title.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.text.copy(alpha = 0.30f)),
                )
                // Stat cards.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(3) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.surface),
                        )
                    }
                }
                // List rows.
                repeat(3) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.text.copy(alpha = 0.10f)),
                    )
                }
            }
        }
    }
}
