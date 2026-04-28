/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("ktlint:standard:backing-property-naming")

package com.containerdashboard.ui.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Filled.ViewInAr: ImageVector
    get() {
        if (_viewInAr != null) {
            return _viewInAr!!
        }
        _viewInAr =
            materialIcon(name = "Filled.ViewInAr") {
                materialPath {
                    moveTo(18.25f, 7.6f)
                    lineToRelative(-5.5f, -3.18f)
                    curveToRelative(-0.46f, -0.27f, -1.04f, -0.27f, -1.5f, 0.0f)
                    lineTo(5.75f, 7.6f)
                    curveToRelative(-0.46f, 0.27f, -0.75f, 0.76f, -0.75f, 1.3f)
                    verticalLineToRelative(6.35f)
                    curveToRelative(0.0f, 0.54f, 0.29f, 1.03f, 0.75f, 1.3f)
                    lineToRelative(5.5f, 3.18f)
                    curveToRelative(0.46f, 0.27f, 1.04f, 0.27f, 1.5f, 0.0f)
                    lineToRelative(5.5f, -3.18f)
                    curveToRelative(0.46f, -0.27f, 0.75f, -0.76f, 0.75f, -1.3f)
                    lineTo(19.0f, 8.9f)
                    curveToRelative(0.0f, -0.54f, -0.29f, -1.03f, -0.75f, -1.3f)
                    close()
                    moveTo(7.0f, 14.96f)
                    verticalLineToRelative(-4.62f)
                    lineToRelative(4.0f, 2.32f)
                    verticalLineToRelative(4.61f)
                    lineToRelative(-4.0f, -2.31f)
                    close()
                    moveTo(12.0f, 10.93f)
                    lineTo(8.0f, 8.61f)
                    lineToRelative(4.0f, -2.31f)
                    lineToRelative(4.0f, 2.31f)
                    lineToRelative(-4.0f, 2.32f)
                    close()
                    moveTo(13.0f, 17.27f)
                    verticalLineToRelative(-4.61f)
                    lineToRelative(4.0f, -2.32f)
                    verticalLineToRelative(4.62f)
                    lineToRelative(-4.0f, 2.31f)
                    close()
                    moveTo(7.0f, 2.0f)
                    lineTo(3.5f, 2.0f)
                    curveTo(2.67f, 2.0f, 2.0f, 2.67f, 2.0f, 3.5f)
                    lineTo(2.0f, 7.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(4.0f, 4.0f)
                    horizontalLineToRelative(3.0f)
                    lineTo(7.0f, 2.0f)
                    close()
                    moveTo(17.0f, 2.0f)
                    horizontalLineToRelative(3.5f)
                    curveToRelative(0.83f, 0.0f, 1.5f, 0.67f, 1.5f, 1.5f)
                    lineTo(22.0f, 7.0f)
                    horizontalLineToRelative(-2.0f)
                    lineTo(20.0f, 4.0f)
                    horizontalLineToRelative(-3.0f)
                    lineTo(17.0f, 2.0f)
                    close()
                    moveTo(7.0f, 22.0f)
                    lineTo(3.5f, 22.0f)
                    curveToRelative(-0.83f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                    lineTo(2.0f, 17.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(3.0f)
                    verticalLineToRelative(2.0f)
                    close()
                    moveTo(17.0f, 22.0f)
                    horizontalLineToRelative(3.5f)
                    curveToRelative(0.83f, 0.0f, 1.5f, -0.67f, 1.5f, -1.5f)
                    lineTo(22.0f, 17.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(-3.0f)
                    verticalLineToRelative(2.0f)
                    close()
                }
            }
        return _viewInAr!!
    }

private var _viewInAr: ImageVector? = null
