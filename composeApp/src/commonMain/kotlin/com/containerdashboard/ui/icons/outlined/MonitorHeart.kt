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

public val Icons.Outlined.MonitorHeart: ImageVector
    get() {
        if (_monitorHeart != null) {
            return _monitorHeart!!
        }
        _monitorHeart =
            materialIcon(name = "Outlined.MonitorHeart") {
                materialPath {
                    moveTo(20.0f, 4.0f)
                    horizontalLineTo(4.0f)
                    curveTo(2.9f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(6.0f)
                    horizontalLineToRelative(16.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(6.0f)
                    curveTo(22.0f, 4.9f, 21.1f, 4.0f, 20.0f, 4.0f)
                    close()
                }
                materialPath {
                    moveTo(20.0f, 18.0f)
                    horizontalLineTo(4.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineTo(2.0f)
                    verticalLineToRelative(3.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(16.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineTo(18.0f)
                    close()
                }
                materialPath {
                    moveTo(14.89f, 7.55f)
                    curveToRelative(-0.34f, -0.68f, -1.45f, -0.68f, -1.79f, 0.0f)
                    lineTo(10.0f, 13.76f)
                    lineToRelative(-1.11f, -2.21f)
                    curveTo(8.72f, 11.21f, 8.38f, 11.0f, 8.0f, 11.0f)
                    horizontalLineTo(2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(5.38f)
                    lineToRelative(1.72f, 3.45f)
                    curveTo(9.28f, 16.79f, 9.62f, 17.0f, 10.0f, 17.0f)
                    reflectiveCurveToRelative(0.72f, -0.21f, 0.89f, -0.55f)
                    lineTo(14.0f, 10.24f)
                    lineToRelative(1.11f, 2.21f)
                    curveTo(15.28f, 12.79f, 15.62f, 13.0f, 16.0f, 13.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(-5.38f)
                    lineTo(14.89f, 7.55f)
                    close()
                }
            }
        return _monitorHeart!!
    }

private var _monitorHeart: ImageVector? = null
