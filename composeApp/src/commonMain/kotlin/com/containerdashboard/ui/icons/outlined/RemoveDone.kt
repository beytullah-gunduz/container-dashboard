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

public val Icons.Outlined.RemoveDone: ImageVector
    get() {
        if (_removeDone != null) {
            return _removeDone!!
        }
        _removeDone =
            materialIcon(name = "Outlined.RemoveDone") {
                materialPath {
                    moveTo(4.84f, 1.98f)
                    lineTo(3.43f, 3.39f)
                    lineToRelative(10.38f, 10.38f)
                    lineToRelative(-1.41f, 1.41f)
                    lineToRelative(-4.24f, -4.24f)
                    lineToRelative(-1.41f, 1.41f)
                    lineToRelative(5.66f, 5.66f)
                    lineToRelative(2.83f, -2.83f)
                    lineToRelative(6.6f, 6.6f)
                    lineToRelative(1.41f, -1.41f)
                    lineTo(4.84f, 1.98f)
                    close()
                    moveTo(18.05f, 12.36f)
                    lineTo(23.0f, 7.4f)
                    lineTo(21.57f, 6.0f)
                    lineToRelative(-4.94f, 4.94f)
                    lineTo(18.05f, 12.36f)
                    close()
                    moveTo(17.34f, 7.4f)
                    lineToRelative(-1.41f, -1.41f)
                    lineToRelative(-2.12f, 2.12f)
                    lineToRelative(1.41f, 1.41f)
                    lineTo(17.34f, 7.4f)
                    close()
                    moveTo(1.08f, 12.35f)
                    lineToRelative(5.66f, 5.66f)
                    lineToRelative(1.41f, -1.41f)
                    lineToRelative(-5.66f, -5.66f)
                    lineTo(1.08f, 12.35f)
                    close()
                }
            }
        return _removeDone!!
    }

private var _removeDone: ImageVector? = null
