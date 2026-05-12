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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad

class QuadEditingHandler {

    companion object {
        const val CORNER_RADIUS = 40f
        const val CORNER_TOUCH_RADIUS = 90f
    }

    fun findTouchedCorner(
        touchPos: Offset,
        quad: Quad,
        containerSize: IntSize,
        displaySize: IntSize
    ): Int {
        return findTouchedCornerCandidates(touchPos, quad, containerSize, displaySize)
            .firstOrNull() ?: -1
    }

    fun findTouchedCornerCandidates(
        touchPos: Offset,
        quad: Quad,
        containerSize: IntSize,
        displaySize: IntSize
    ): List<Int> {
        val corners = getCornerPositions(quad, containerSize, displaySize)
        return corners
            .mapIndexed { index, corner -> index to (touchPos - corner).getDistance() }
            .filter { (_, distance) -> distance < CORNER_TOUCH_RADIUS }
            .sortedBy { (_, distance) -> distance }
            .map { (index, _) -> index }
    }

    fun updateQuadCorner(quad: Quad, cornerIndex: Int, delta: Offset): Quad {
        val normalizedDelta = Point(delta.x.toDouble(), delta.y.toDouble())
        val candidate = when (cornerIndex) {
            0 -> quad.copy(topLeft = clampPoint(quad.topLeft + normalizedDelta))
            1 -> quad.copy(topRight = clampPoint(quad.topRight + normalizedDelta))
            2 -> quad.copy(bottomRight = clampPoint(quad.bottomRight + normalizedDelta))
            3 -> quad.copy(bottomLeft = clampPoint(quad.bottomLeft + normalizedDelta))
            else -> quad
        }
        return if (candidate.isConvex()) candidate else quad
    }

    private fun getCornerPositions(quad: Quad, containerSize: IntSize, displaySize: IntSize): List<Offset> {
        return listOf(
            QuadCoordinateUtils.normalizedToScreen(quad.topLeft, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.topRight, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.bottomRight, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.bottomLeft, containerSize, displaySize)
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
