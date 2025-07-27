/*
 * Copyright 2025 Pierre-Yves Nicolas
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.mydomain.myscan

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

@Immutable
data class LiveAnalysisState(
    val inferenceTime: Long = 0L,
    val binaryMask: Bitmap? = null,
    val documentQuad: Quad? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
