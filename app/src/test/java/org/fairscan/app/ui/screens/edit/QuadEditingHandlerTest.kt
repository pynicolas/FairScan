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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset as AssertJOffset
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.Before
import org.junit.Test

class QuadEditingHandlerTest {

    private lateinit var handler: QuadEditingHandler
    private val containerSize = IntSize(1000, 800)
    private val displaySize = IntSize(800, 600)

    private val centeredQuad = Quad(
        topLeft = Point(0.2, 0.2),
        topRight = Point(0.8, 0.2),
        bottomRight = Point(0.8, 0.8),
        bottomLeft = Point(0.2, 0.8)
    )

    @Before
    fun setUp() {
        handler = QuadEditingHandler()
    }

    @Test
    fun findTouchedCorner_detectsAllCornersAndMisses() {
        // All four corners should be detected
        val corners = listOf(centeredQuad.topLeft, centeredQuad.topRight, centeredQuad.bottomRight, centeredQuad.bottomLeft)
        corners.forEachIndexed { index, corner ->
            val touchPos = QuadCoordinateUtils.normalizedToScreen(corner, containerSize, displaySize)
            assertThat(handler.findTouchedCorner(touchPos, centeredQuad, containerSize, displaySize)).isEqualTo(index)
        }

        // Near corner (within radius) should also be detected
        val topLeftScreen = QuadCoordinateUtils.normalizedToScreen(centeredQuad.topLeft, containerSize, displaySize)
        val nearTouch = Offset(topLeftScreen.x + 20f, topLeftScreen.y + 15f)
        assertThat(handler.findTouchedCorner(nearTouch, centeredQuad, containerSize, displaySize)).isEqualTo(0)

        // Far from corners should return -1
        val centerTouch = QuadCoordinateUtils.normalizedToScreen(Point(0.5, 0.5), containerSize, displaySize)
        assertThat(handler.findTouchedCorner(centerTouch, centeredQuad, containerSize, displaySize)).isEqualTo(-1)
    }

    @Test
    fun findTouchedEdge_detectsAllEdgesAndMisses() {
        // Test all four edge midpoints
        val edgeMidpoints = listOf(
            Point((centeredQuad.topLeft.x + centeredQuad.topRight.x) / 2, centeredQuad.topLeft.y),
            Point(centeredQuad.topRight.x, (centeredQuad.topRight.y + centeredQuad.bottomRight.y) / 2),
            Point((centeredQuad.bottomRight.x + centeredQuad.bottomLeft.x) / 2, centeredQuad.bottomRight.y),
            Point(centeredQuad.topLeft.x, (centeredQuad.bottomLeft.y + centeredQuad.topLeft.y) / 2)
        )
        edgeMidpoints.forEachIndexed { index, midpoint ->
            val touchPos = QuadCoordinateUtils.normalizedToScreen(midpoint, containerSize, displaySize)
            assertThat(handler.findTouchedEdge(touchPos, centeredQuad, containerSize, displaySize)).isEqualTo(index)
        }

        // Far from edges should return -1
        assertThat(handler.findTouchedEdge(Offset(10f, 10f), centeredQuad, containerSize, displaySize)).isEqualTo(-1)
    }

    @Test
    fun updateQuadCorner_movesCorrectCornerAndClampsTooBounds() {
        // Move each corner and verify only that corner changes
        val deltas = listOf(Offset(0.1f, 0.1f), Offset(-0.1f, 0.1f), Offset(-0.1f, -0.1f), Offset(0.1f, -0.1f))
        val expectedPositions = listOf(Point(0.3, 0.3), Point(0.7, 0.3), Point(0.7, 0.7), Point(0.3, 0.7))

        deltas.forEachIndexed { index, delta ->
            val result = handler.updateQuadCorner(centeredQuad, index, delta)
            val movedCorner = when (index) {
                0 -> result.topLeft
                1 -> result.topRight
                2 -> result.bottomRight
                else -> result.bottomLeft
            }
            assertThat(movedCorner.x).isCloseTo(expectedPositions[index].x, AssertJOffset.offset(0.001))
            assertThat(movedCorner.y).isCloseTo(expectedPositions[index].y, AssertJOffset.offset(0.001))
        }

        // Invalid index returns unchanged quad
        assertThat(handler.updateQuadCorner(centeredQuad, 5, Offset(0.1f, 0.1f))).isEqualTo(centeredQuad)

        // Zero delta returns unchanged quad
        assertThat(handler.updateQuadCorner(centeredQuad, 0, Offset(0f, 0f))).isEqualTo(centeredQuad)

        // Clamping to min bounds (0)
        var result = handler.updateQuadCorner(centeredQuad, 0, Offset(-0.5f, -0.5f))
        assertThat(result.topLeft.x).isEqualTo(0.0)
        assertThat(result.topLeft.y).isEqualTo(0.0)

        // Clamping to max bounds (1)
        result = handler.updateQuadCorner(centeredQuad, 2, Offset(0.5f, 0.5f))
        assertThat(result.bottomRight.x).isEqualTo(1.0)
        assertThat(result.bottomRight.y).isEqualTo(1.0)
    }

    @Test
    fun updateQuadEdge_movesBothCornersOfEdgeAndClamps() {
        // Top edge (index 0) - moves topLeft and topRight
        var result = handler.updateQuadEdge(centeredQuad, 0, Offset(0.0f, 0.1f))
        assertThat(result.topLeft.y).isCloseTo(0.3, AssertJOffset.offset(0.001))
        assertThat(result.topRight.y).isCloseTo(0.3, AssertJOffset.offset(0.001))
        assertThat(result.bottomRight).isEqualTo(centeredQuad.bottomRight)
        assertThat(result.bottomLeft).isEqualTo(centeredQuad.bottomLeft)

        // Right edge (index 1) - moves topRight and bottomRight
        result = handler.updateQuadEdge(centeredQuad, 1, Offset(-0.1f, 0.0f))
        assertThat(result.topRight.x).isCloseTo(0.7, AssertJOffset.offset(0.001))
        assertThat(result.bottomRight.x).isCloseTo(0.7, AssertJOffset.offset(0.001))
        assertThat(result.topLeft).isEqualTo(centeredQuad.topLeft)
        assertThat(result.bottomLeft).isEqualTo(centeredQuad.bottomLeft)

        // Bottom edge (index 2) - moves bottomRight and bottomLeft
        result = handler.updateQuadEdge(centeredQuad, 2, Offset(0.0f, -0.1f))
        assertThat(result.bottomRight.y).isCloseTo(0.7, AssertJOffset.offset(0.001))
        assertThat(result.bottomLeft.y).isCloseTo(0.7, AssertJOffset.offset(0.001))
        assertThat(result.topLeft).isEqualTo(centeredQuad.topLeft)
        assertThat(result.topRight).isEqualTo(centeredQuad.topRight)

        // Left edge (index 3) - moves topLeft and bottomLeft
        result = handler.updateQuadEdge(centeredQuad, 3, Offset(0.1f, 0.0f))
        assertThat(result.topLeft.x).isCloseTo(0.3, AssertJOffset.offset(0.001))
        assertThat(result.bottomLeft.x).isCloseTo(0.3, AssertJOffset.offset(0.001))
        assertThat(result.topRight).isEqualTo(centeredQuad.topRight)
        assertThat(result.bottomRight).isEqualTo(centeredQuad.bottomRight)

        // Invalid index returns unchanged quad
        assertThat(handler.updateQuadEdge(centeredQuad, 5, Offset(0.1f, 0.1f))).isEqualTo(centeredQuad)

        // Zero delta returns unchanged quad
        assertThat(handler.updateQuadEdge(centeredQuad, 0, Offset(0f, 0f))).isEqualTo(centeredQuad)
    }

    @Test
    fun updateQuadEdge_clampsToImageBounds() {
        // Clamp to min bounds
        var result = handler.updateQuadEdge(centeredQuad, 0, Offset(-0.5f, -0.5f))
        assertThat(result.topLeft.x).isEqualTo(0.0)
        assertThat(result.topLeft.y).isEqualTo(0.0)
        assertThat(result.topRight.y).isEqualTo(0.0)

        // Clamp to max bounds
        result = handler.updateQuadEdge(centeredQuad, 2, Offset(0.5f, 0.5f))
        assertThat(result.bottomRight.x).isEqualTo(1.0)
        assertThat(result.bottomRight.y).isEqualTo(1.0)
        assertThat(result.bottomLeft.y).isEqualTo(1.0)
    }
}
