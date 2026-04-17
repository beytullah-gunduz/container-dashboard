package com.containerdashboard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.containerdashboard.renderAppIconAwt

@Composable
actual fun AppBrandMark(modifier: Modifier) {
    val bitmap = remember { renderAppIconAwt(48).toComposeImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = "Container Dashboard",
        modifier = modifier,
    )
}
