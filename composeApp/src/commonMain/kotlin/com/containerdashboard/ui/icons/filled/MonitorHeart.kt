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

public val Icons.Filled.MonitorHeart: ImageVector
    get() {
        if (_monitorHeart != null) {
            return _monitorHeart!!
        }
        _monitorHeart =
            materialIcon(name = "Filled.MonitorHeart") {
                materialPath {
                    moveTo(15.11f, 12.45f)
                    lineTo(14.0f, 10.24f)
                    lineToRelative(-3.11f, 6.21f)
                    curveTo(10.73f, 16.79f, 10.38f, 17.0f, 10.0f, 17.0f)
                    reflectiveCurveToRelative(-0.73f, -0.21f, -0.89f, -0.55f)
                    lineTo(7.38f, 13.0f)
                    horizontalLineTo(2.0f)
                    verticalLineToRelative(5.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(16.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineToRelative(-5.0f)
                    horizontalLineToRelative(-6.0f)
                    curveTo(15.62f, 13.0f, 15.27f, 12.79f, 15.11f, 12.45f)
                    close()
                }
                materialPath {
                    moveTo(20.0f, 4.0f)
                    horizontalLineTo(4.0f)
                    curveTo(2.9f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
                    verticalLineToRelative(5.0f)
                    horizontalLineToRelative(6.0f)
                    curveToRelative(0.38f, 0.0f, 0.73f, 0.21f, 0.89f, 0.55f)
                    lineTo(10.0f, 13.76f)
                    lineToRelative(3.11f, -6.21f)
                    curveToRelative(0.34f, -0.68f, 1.45f, -0.68f, 1.79f, 0.0f)
                    lineTo(16.62f, 11.0f)
                    horizontalLineTo(22.0f)
                    verticalLineTo(6.0f)
                    curveTo(22.0f, 4.9f, 21.1f, 4.0f, 20.0f, 4.0f)
                    close()
                }
            }
        return _monitorHeart!!
    }

private var _monitorHeart: ImageVector? = null
