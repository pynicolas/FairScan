/*
 * Copyright 2026 Philipp Hasper
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
package org.fairscan.app.ui.screens.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Point

object QuadCoordinateUtils {

    fun calculateDisplaySize(
        bitmapWidth: Int,
        bitmapHeight: Int,
        containerSize: IntSize
    ): IntSize {
        val imageAspectRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        val containerAspectRatio = containerSize.width / containerSize.height.toFloat()

        return if (imageAspectRatio > containerAspectRatio) {
            IntSize(containerSize.width, (containerSize.width / imageAspectRatio).toInt())
        } else {
            IntSize((containerSize.height * imageAspectRatio).toInt(), containerSize.height)
        }
    }

    fun normalizedToScreen(point: Point, containerSize: IntSize, displaySize: IntSize): Offset {
        val offsetX = (containerSize.width - displaySize.width) / 2
        val offsetY = (containerSize.height - displaySize.height) / 2
        return Offset(
            x = (point.x * displaySize.width).toFloat() + offsetX,
            y = (point.y * displaySize.height).toFloat() + offsetY
        )
    }

    fun screenDeltaToNormalized(delta: Offset, displaySize: IntSize): Offset {
        return Offset(
            x = delta.x / displaySize.width,
            y = delta.y / displaySize.height
        )
    }

    fun getImageOffset(containerSize: IntSize, displaySize: IntSize): IntSize {
        return IntSize(
            (containerSize.width - displaySize.width) / 2,
            (containerSize.height - displaySize.height) / 2
        )
    }
}
