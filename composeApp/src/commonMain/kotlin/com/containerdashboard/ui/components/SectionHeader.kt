package com.containerdashboard.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.icons.filled.ChevronRight
import com.containerdashboard.ui.icons.filled.ExpandMore
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.util.hoverHighlight

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .hoverHighlight(shape = RoundedCornerShape(Radius.md))
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
