package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.Radius
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.check
import org.jetbrains.compose.resources.painterResource

/**
 * 14dp desktop-sized checkbox. Replacement for Material3 `Checkbox` in dense
 * tables where the built-in 48dp touch target wastes vertical space.
 */
@Composable
internal fun CompactCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val outlineColor =
        if (enabled) {
            MaterialTheme.colorScheme.outline
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        }
    val fillColor =
        when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            checked -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        }
    Box(
        modifier =
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(fillColor)
                .border(
                    width = 1.2.dp,
                    color = if (checked && enabled) MaterialTheme.colorScheme.primary else outlineColor,
                    shape = RoundedCornerShape(Radius.xs),
                ).clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(Res.drawable.check),
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
