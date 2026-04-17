package com.containerdashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val height = if (compact) 30.dp else 40.dp
    val iconSize = if (compact) 14.dp else 18.dp
    val horizontalPadding = if (compact) 8.dp else 12.dp
    val itemSpacing = if (compact) 6.dp else 8.dp
    val textStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val clearButtonSize = if (compact) 16.dp else 18.dp
    val clearIconSize = if (compact) 12.dp else 16.dp

    Surface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(if (compact) 6.dp else 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle =
                        TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = textStyle.fontSize,
                        ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(clearButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(clearIconSize),
                    )
                }
            }
        }
    }
}
