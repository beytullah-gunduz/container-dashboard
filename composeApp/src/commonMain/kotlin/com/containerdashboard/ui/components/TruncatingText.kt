package com.containerdashboard.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing

/**
 * Text that shows the full value in a tooltip *only when it visibly truncates*.
 *
 * Drop-in replacement for single-line ellipsized [Text] call sites where the
 * value can be longer than the column (container names, image repo/tag,
 * compose project names, ...). No tooltip is shown when the text fits, so
 * short values don't get a distracting hover pop-up.
 *
 * The truncation signal comes from [Text.onTextLayout]; the `modifier` is
 * applied to the outer [TooltipArea] so weight/width constraints continue to
 * work from the parent Row/Column.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TruncatingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    maxLines: Int = 1,
) {
    var truncated by remember(text) { mutableStateOf(false) }
    TooltipArea(
        modifier = modifier,
        tooltip = {
            if (truncated) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        delayMillis = 400,
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> truncated = result.hasVisualOverflow },
        )
    }
}
