/*
 * Copyright 2025-2026 The FairScan authors
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
package org.fairscan.app.ui.screens.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.scaledTo
import kotlin.math.max
import kotlin.math.min

@Composable
fun QuadOverlay(
    quad: Quad,
    containerSize: IntSize,
    displaySize: IntSize,
    modifier: Modifier = Modifier
) {
    val quadColor = MaterialTheme.colorScheme.primary
    val handleColor = quadColor.copy(alpha = 0.5f)

    Canvas(modifier = modifier
        .fillMaxSize()
        // Android limits the exclusion areas, so we cannot simply exclude the whole quad/image.
        // Instead, the corners must get excluded individually.
        .systemGestureExclusion { cornerExclusionRect(quad.topLeft, containerSize, displaySize) }
        .systemGestureExclusion { cornerExclusionRect(quad.topRight, containerSize, displaySize) }
        .systemGestureExclusion { cornerExclusionRect(quad.bottomRight, containerSize, displaySize) }
        .systemGestureExclusion { cornerExclusionRect(quad.bottomLeft, containerSize, displaySize) }
    ) {
        val scaledQuad = quad.scaledTo(
            fromWidth = 1,
            fromHeight = 1,
            toWidth = displaySize.width,
            toHeight = displaySize.height
        )

        val offset = QuadCoordinateUtils.getImageOffset(containerSize, displaySize)
        val corners = listOf(
            scaledQuad.topLeft.toOffset(),
            scaledQuad.topRight.toOffset(),
            scaledQuad.bottomRight.toOffset(),
            scaledQuad.bottomLeft.toOffset()
        ).map { it.copy(x = it.x + offset.width, y = it.y + offset.height) }

        // Draw edges
        for (i in 0 until 4) {
            drawLine(
                color = quadColor,
                start = corners[i],
                end = corners[(i + 1) % 4],
                strokeWidth = 10.0f
            )
        }

        // Draw corner handles
        corners.forEach { corner ->
            drawCircle(
                color = handleColor,
                radius = QuadEditingHandler.CORNER_RADIUS,
                center = corner
            )
        }

    }
}

fun Point.toOffset() = Offset(x.toFloat(), y.toFloat())

private fun cornerExclusionRect(
    corner: Point,
    containerSize: IntSize,
    displaySize: IntSize
): Rect {
    val pos = QuadCoordinateUtils.normalizedToScreen(corner, containerSize, displaySize)
    val r = QuadEditingHandler.CORNER_TOUCH_RADIUS
    return Rect(max(.0f, pos.x - r),
                max(.0f, pos.y - r),
                min(containerSize.width.toFloat(), pos.x + r),
                min(containerSize.height.toFloat(), pos.y + r)
    )
}
