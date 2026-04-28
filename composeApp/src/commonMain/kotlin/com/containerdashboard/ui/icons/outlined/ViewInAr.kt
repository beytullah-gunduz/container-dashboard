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

package com.containerdashboard.ui.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.ViewInAr: ImageVector
    get() {
        if (_viewInAr != null) {
            return _viewInAr!!
        }
        _viewInAr =
            materialIcon(name = "Outlined.ViewInAr") {
                materialPath {
                    moveTo(3.0f, 4.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(1.0f)
                    horizontalLineTo(4.0f)
                    curveTo(2.34f, 1.0f, 1.0f, 2.34f, 1.0f, 4.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(4.0f)
                    close()
                }
                materialPath {
                    moveTo(3.0f, 20.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(1.0f)
                    verticalLineToRelative(2.0f)
                    curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(4.0f)
                    curveTo(3.45f, 21.0f, 3.0f, 20.55f, 3.0f, 20.0f)
                    close()
                }
                materialPath {
                    moveTo(20.0f, 1.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(4.0f)
                    curveTo(23.0f, 2.34f, 21.66f, 1.0f, 20.0f, 1.0f)
                    close()
                }
                materialPath {
                    moveTo(21.0f, 20.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineTo(20.0f)
                    close()
                }
                materialPath {
                    moveTo(19.0f, 14.87f)
                    verticalLineTo(9.13f)
                    curveToRelative(0.0f, -0.72f, -0.38f, -1.38f, -1.0f, -1.73f)
                    lineToRelative(-5.0f, -2.88f)
                    curveToRelative(-0.31f, -0.18f, -0.65f, -0.27f, -1.0f, -0.27f)
                    reflectiveCurveToRelative(-0.69f, 0.09f, -1.0f, 0.27f)
                    lineTo(6.0f, 7.39f)
                    curveTo(5.38f, 7.75f, 5.0f, 8.41f, 5.0f, 9.13f)
                    verticalLineToRelative(5.74f)
                    curveToRelative(0.0f, 0.72f, 0.38f, 1.38f, 1.0f, 1.73f)
                    lineToRelative(5.0f, 2.88f)
                    curveToRelative(0.31f, 0.18f, 0.65f, 0.27f, 1.0f, 0.27f)
                    reflectiveCurveToRelative(0.69f, -0.09f, 1.0f, -0.27f)
                    lineToRelative(5.0f, -2.88f)
                    curveTo(18.62f, 16.25f, 19.0f, 15.59f, 19.0f, 14.87f)
                    close()
                    moveTo(11.0f, 17.17f)
                    lineToRelative(-4.0f, -2.3f)
                    verticalLineToRelative(-4.63f)
                    lineToRelative(4.0f, 2.33f)
                    verticalLineTo(17.17f)
                    close()
                    moveTo(12.0f, 10.84f)
                    lineTo(8.04f, 8.53f)
                    lineTo(12.0f, 6.25f)
                    lineToRelative(3.96f, 2.28f)
                    lineTo(12.0f, 10.84f)
                    close()
                    moveTo(17.0f, 14.87f)
                    lineToRelative(-4.0f, 2.3f)
                    verticalLineToRelative(-4.6f)
                    lineToRelative(4.0f, -2.33f)
                    verticalLineTo(14.87f)
                    close()
                }
            }
        return _viewInAr!!
    }

private var _viewInAr: ImageVector? = null
