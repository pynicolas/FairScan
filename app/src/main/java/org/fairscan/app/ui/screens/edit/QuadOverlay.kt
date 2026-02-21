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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.scaledTo
import kotlin.math.atan2

@Composable
fun QuadOverlay(
    quad: Quad,
    containerSize: IntSize,
    displaySize: IntSize,
    modifier: Modifier = Modifier
) {
    val quadColor = MaterialTheme.colorScheme.primary
    val handleColor = quadColor.copy(alpha = 0.5f)

    Canvas(modifier = modifier.fillMaxSize()) {
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

        // Draw edge handles
        drawEdgeHandles(corners, handleColor)
    }
}

private fun DrawScope.drawEdgeHandles(
    corners: List<Offset>,
    handleColor: androidx.compose.ui.graphics.Color
) {
    for (i in 0 until 4) {
        val from = corners[i]
        val to = corners[(i + 1) % 4]
        val midpoint = Offset((from.x + to.x) / 2, (from.y + to.y) / 2)

        val edgeAngle = atan2(
            (to.y - from.y).toDouble(),
            (to.x - from.x).toDouble()
        ) * 180 / Math.PI

        rotate(degrees = edgeAngle.toFloat(), pivot = midpoint) {
            val cornerRadius = QuadEditingHandler.EDGE_HANDLE_SIZE.height / 2
            drawRoundRect(
                color = handleColor,
                topLeft = Offset(
                    midpoint.x - QuadEditingHandler.EDGE_HANDLE_SIZE.width / 2,
                    midpoint.y - QuadEditingHandler.EDGE_HANDLE_SIZE.height / 2
                ),
                size = QuadEditingHandler.EDGE_HANDLE_SIZE,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )
        }
    }
}

fun Point.toOffset() = Offset(x.toFloat(), y.toFloat())
