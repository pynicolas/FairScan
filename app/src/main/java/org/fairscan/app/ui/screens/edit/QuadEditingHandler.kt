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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad

class QuadEditingHandler {

    companion object {
        const val CORNER_RADIUS = 40f
        val EDGE_HANDLE_SIZE = Size(90f, 40f)
    }

    fun findTouchedCorner(
        touchPos: Offset,
        quad: Quad,
        containerSize: IntSize,
        displaySize: IntSize
    ): Int {
        val corners = getCornerPositions(quad, containerSize, displaySize)
        return corners.indexOfFirst { corner ->
            (touchPos - corner).getDistance() < CORNER_RADIUS * 1.5f
        }
    }

    fun findTouchedEdge(
        touchPos: Offset,
        quad: Quad,
        containerSize: IntSize,
        displaySize: IntSize
    ): Int {
        val corners = getCornerPositions(quad, containerSize, displaySize)
        val edgeMidpoints = getEdgeMidpoints(corners)
        return edgeMidpoints.indexOfFirst { midpoint ->
            (touchPos - midpoint).getDistance() < EDGE_HANDLE_SIZE.width
        }
    }

    fun updateQuadCorner(quad: Quad, cornerIndex: Int, delta: Offset): Quad {
        val normalizedDelta = Point(delta.x.toDouble(), delta.y.toDouble())
        return when (cornerIndex) {
            0 -> quad.copy(topLeft = clampPoint(quad.topLeft + normalizedDelta))
            1 -> quad.copy(topRight = clampPoint(quad.topRight + normalizedDelta))
            2 -> quad.copy(bottomRight = clampPoint(quad.bottomRight + normalizedDelta))
            3 -> quad.copy(bottomLeft = clampPoint(quad.bottomLeft + normalizedDelta))
            else -> quad
        }
    }

    fun updateQuadEdge(quad: Quad, edgeIndex: Int, delta: Offset): Quad {
        val normalizedDelta = Point(delta.x.toDouble(), delta.y.toDouble())
        return when (edgeIndex) {
            0 -> quad.copy( // top edge
                topLeft = clampPoint(quad.topLeft + normalizedDelta),
                topRight = clampPoint(quad.topRight + normalizedDelta)
            )
            1 -> quad.copy( // right edge
                topRight = clampPoint(quad.topRight + normalizedDelta),
                bottomRight = clampPoint(quad.bottomRight + normalizedDelta)
            )
            2 -> quad.copy( // bottom edge
                bottomRight = clampPoint(quad.bottomRight + normalizedDelta),
                bottomLeft = clampPoint(quad.bottomLeft + normalizedDelta)
            )
            3 -> quad.copy( // left edge
                bottomLeft = clampPoint(quad.bottomLeft + normalizedDelta),
                topLeft = clampPoint(quad.topLeft + normalizedDelta)
            )
            else -> quad
        }
    }

    private fun getCornerPositions(quad: Quad, containerSize: IntSize, displaySize: IntSize): List<Offset> {
        return listOf(
            QuadCoordinateUtils.normalizedToScreen(quad.topLeft, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.topRight, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.bottomRight, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.bottomLeft, containerSize, displaySize)
        )
    }

    private fun getEdgeMidpoints(corners: List<Offset>): List<Offset> {
        return listOf(
            Offset((corners[0].x + corners[1].x) / 2, (corners[0].y + corners[1].y) / 2),
            Offset((corners[1].x + corners[2].x) / 2, (corners[1].y + corners[2].y) / 2),
            Offset((corners[2].x + corners[3].x) / 2, (corners[2].y + corners[3].y) / 2),
            Offset((corners[3].x + corners[0].x) / 2, (corners[3].y + corners[0].y) / 2)
        )
    }

    private fun clampPoint(point: Point): Point {
        return Point(
            point.x.coerceIn(0.0, 1.0),
            point.y.coerceIn(0.0, 1.0)
        )
    }

    private operator fun Point.plus(other: Point): Point {
        return Point(this.x + other.x, this.y + other.y)
    }
}
