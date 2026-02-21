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

    private fun closeTopCornersQuadForTouchRadius(): Quad {
        val radiusPx = QuadEditingHandler.CORNER_TOUCH_RADIUS
        // Keep the two top corners comfortably inside each other's touch radius.
        val separationPx = radiusPx * 0.8f
        val normalizedHalfSeparation = (separationPx / displaySize.width) / 2.0
        val centerX = 0.5
        return Quad(
            topLeft = Point(centerX - normalizedHalfSeparation, 0.2),
            topRight = Point(centerX + normalizedHalfSeparation, 0.2),
            bottomRight = Point(0.8, 0.8),
            bottomLeft = Point(0.2, 0.8)
        )
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

        // Outside visual corner radius but inside expanded touch radius.
        val outsideVisualButTouchable = Offset(topLeftScreen.x + 70f, topLeftScreen.y)
        assertThat((outsideVisualButTouchable - topLeftScreen).getDistance())
            .isGreaterThan(QuadEditingHandler.CORNER_RADIUS)
        assertThat(handler.findTouchedCorner(outsideVisualButTouchable, centeredQuad, containerSize, displaySize))
            .isEqualTo(0)

        // Far from corners should return -1
        val centerTouch = QuadCoordinateUtils.normalizedToScreen(Point(0.5, 0.5), containerSize, displaySize)
        assertThat(handler.findTouchedCorner(centerTouch, centeredQuad, containerSize, displaySize)).isEqualTo(-1)
    }

    @Test
    fun findTouchedCorner_selectsClosestCornerWhenMultipleAreInTouchRadius() {
        // Two corners close together so their touch areas overlap.
        val closeTopCornersQuad = closeTopCornersQuadForTouchRadius()
        val topLeftScreen = QuadCoordinateUtils.normalizedToScreen(closeTopCornersQuad.topLeft, containerSize, displaySize)
        val topRightScreen = QuadCoordinateUtils.normalizedToScreen(closeTopCornersQuad.topRight, containerSize, displaySize)
        val towardCornerOffset = QuadEditingHandler.CORNER_TOUCH_RADIUS * 0.2f

        // Touch a bit to the left of topRight — inside both radii but closer to topRight (index 1).
        val touchCloserToTopRight = Offset(topRightScreen.x - towardCornerOffset, topRightScreen.y)
        assertThat(handler.findTouchedCorner(touchCloserToTopRight, closeTopCornersQuad, containerSize, displaySize))
            .isEqualTo(1)

        // Touch a bit to the right of topLeft — inside both radii but closer to topLeft (index 0).
        val touchCloserToTopLeft = Offset(topLeftScreen.x + towardCornerOffset, topLeftScreen.y)
        assertThat(handler.findTouchedCorner(touchCloserToTopLeft, closeTopCornersQuad, containerSize, displaySize))
            .isEqualTo(0)

        // Exact midpoint — equal distance to both; either index 0 or 1 is acceptable.
        val midpointTouch = Offset((topLeftScreen.x + topRightScreen.x) / 2f, topLeftScreen.y)
        assertThat(handler.findTouchedCorner(midpointTouch, closeTopCornersQuad, containerSize, displaySize))
            .isIn(0, 1)
    }

    @Test
    fun findTouchedCornerCandidates_returnsAllCornersInRadiusSortedByDistance() {
        // Single corner in range: only that corner returned.
        val topLeftScreen = QuadCoordinateUtils.normalizedToScreen(centeredQuad.topLeft, containerSize, displaySize)
        val single = handler.findTouchedCornerCandidates(topLeftScreen, centeredQuad, containerSize, displaySize)
        assertThat(single).containsExactly(0)

        // No corner in range: empty list.
        val farAway = QuadCoordinateUtils.normalizedToScreen(Point(0.5, 0.5), containerSize, displaySize)
        assertThat(handler.findTouchedCornerCandidates(farAway, centeredQuad, containerSize, displaySize)).isEmpty()

        // Use a quad whose top two corners are spaced relative to CORNER_TOUCH_RADIUS.
        val closeTopCornersQuad = closeTopCornersQuadForTouchRadius()
        val closeTL = QuadCoordinateUtils.normalizedToScreen(closeTopCornersQuad.topLeft, containerSize, displaySize)
        val closeTR = QuadCoordinateUtils.normalizedToScreen(closeTopCornersQuad.topRight, containerSize, displaySize)
        val towardCornerOffset = QuadEditingHandler.CORNER_TOUCH_RADIUS * 0.2f

        // Touch at midpoint: both in range, order may vary — but both must be present.
        val midpoint = Offset((closeTL.x + closeTR.x) / 2f, closeTL.y)
        val bothCandidates = handler.findTouchedCornerCandidates(midpoint, closeTopCornersQuad, containerSize, displaySize)
        assertThat(bothCandidates).containsExactlyInAnyOrder(0, 1)

        // Touch closer to topRight (index 1): topRight must be first in the list.
        val touchNearTR = Offset(closeTR.x - towardCornerOffset, closeTR.y)
        val overlap = handler.findTouchedCornerCandidates(touchNearTR, closeTopCornersQuad, containerSize, displaySize)
        assertThat(overlap.first()).isEqualTo(1)   // topRight is closest
        assertThat(overlap).contains(0)            // topLeft also a candidate

        // Touch closer to topLeft (index 0): topLeft must be first.
        val touchNearTL = Offset(closeTL.x + towardCornerOffset, closeTL.y)
        val overlapTL = handler.findTouchedCornerCandidates(touchNearTL, closeTopCornersQuad, containerSize, displaySize)
        assertThat(overlapTL.first()).isEqualTo(0)
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


    // ── Convexity enforcement tests ──────────────────────────────────────

    @Test
    fun updateQuadCorner_rejectsConcaveResult() {
        // Drag the topLeft corner past the diagonal to create a concave quad.
        // Moving topLeft far to the right and down should make the quad concave.
        val result = handler.updateQuadCorner(centeredQuad, 0, Offset(0.7f, 0.7f))
        // The result should still be convex, meaning the move was rejected
        // (the original quad is returned).
        assertThat(result).isEqualTo(centeredQuad)
    }

    @Test
    fun updateQuadCorner_allowsConvexResult() {
        // A small move that keeps the quad convex should be allowed.
        val result = handler.updateQuadCorner(centeredQuad, 0, Offset(0.05f, 0.05f))
        // The corner should have moved.
        assertThat(result.topLeft.x).isCloseTo(0.25, AssertJOffset.offset(0.001))
        assertThat(result.topLeft.y).isCloseTo(0.25, AssertJOffset.offset(0.001))
    }


    @Test
    fun updateQuadCorner_allowsFixingConcaveQuad() {
        // Start with a concave quad (topLeft is pushed too far inward).
        val concaveQuad = Quad(
            topLeft = Point(0.7, 0.7),  // past the center, making it concave
            topRight = Point(0.8, 0.2),
            bottomRight = Point(0.8, 0.8),
            bottomLeft = Point(0.2, 0.8)
        )
        // Move topLeft back outward to restore convexity.
        val result = handler.updateQuadCorner(concaveQuad, 0, Offset(-0.5f, -0.5f))
        // The move should be allowed because the result is convex.
        assertThat(result.topLeft.x).isCloseTo(0.2, AssertJOffset.offset(0.001))
        assertThat(result.topLeft.y).isCloseTo(0.2, AssertJOffset.offset(0.001))
    }


    @Test
    fun updateQuadCorner_rejectsMoveAlongEdgeThatCreatesConcavity() {
        // Start with a nearly-flat quad that's still convex.
        val narrowQuad = Quad(
            topLeft = Point(0.2, 0.2),
            topRight = Point(0.8, 0.2),
            bottomRight = Point(0.8, 0.3),
            bottomLeft = Point(0.2, 0.3)
        )
        // Drag bottomLeft upward past the top edge — should be rejected.
        val result = handler.updateQuadCorner(narrowQuad, 3, Offset(0.0f, -0.2f))
        assertThat(result).isEqualTo(narrowQuad)
    }
}
