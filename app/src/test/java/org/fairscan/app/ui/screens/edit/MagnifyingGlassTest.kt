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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/** Acceptable offset for float comparisons **/
private val FLOAT_OFFSET = org.assertj.core.data.Offset.offset(0.0001f)

class MagnifyingGlassTest {

    private val configPx = LoupeLayoutConfig(
        loupeRadius = 150f,
        verticalOffset = 200f,
        screenMargin = 20f,
    )
    private val containerWidth = 1080f

    @Test
    fun abovePlacement_whenPlentyOfRoomAbove() {
        // Finger in the middle of the screen, plenty of room everywhere
        val drag = Offset(540f, 800f)
        val result = computeLoupeCenter(drag, configPx, containerWidth)

        // Expected Y: 800 - 200 - 150 = 450
        assertThat(result.y).isCloseTo(450f, FLOAT_OFFSET)
        // X should stay centred on finger
        assertThat(result.x).isCloseTo(540f, FLOAT_OFFSET)
    }

    @Test
    fun abovePlacement_clampsXToLeftEdge() {
        // Finger very close to the left edge
        val drag = Offset(50f, 800f)
        val result = computeLoupeCenter(drag, configPx, containerWidth)

        // X should be clamped to screenMargin + loupeRadius = 20 + 150 = 170
        assertThat(result.x).isCloseTo(170f, FLOAT_OFFSET)
        // Still placed above
        assertThat(result.y).isCloseTo(450f, FLOAT_OFFSET)
    }

    @Test
    fun abovePlacement_clampsXToRightEdge() {
        // Finger very close to the right edge
        val drag = Offset(1060f, 800f)
        val result = computeLoupeCenter(drag, configPx, containerWidth)

        // X should be clamped to containerWidth - screenMargin - loupeRadius = 1080 - 20 - 150 = 910
        assertThat(result.x).isCloseTo(910f, FLOAT_OFFSET)
        // Still placed above
        assertThat(result.y).isCloseTo(450f, FLOAT_OFFSET)
    }

    @Test
    fun leftPlacement_whenNotEnoughRoomAbove() {
        // Finger near the top: Y must be small enough that above placement fails
        // above center Y = dragY - verticalOffset - loupeRadius
        // condition: aboveCenterY - loupeRadius >= screenMargin
        // => dragY - 200 - 150 - 150 >= 20 => dragY >= 520
        // Use dragY = 300 (not enough room above)
        // Finger at centre-X so there IS room to the left
        val drag = Offset(540f, 300f)
        val result = computeLoupeCenter(drag, configPx, containerWidth)

        // Left center X = 540 - 200 - 150 = 190
        // leftCenterX - loupeRadius = 190 - 150 = 40 >= 20 ✓
        assertThat(result.x).isCloseTo(190f, FLOAT_OFFSET)
        // Y should equal dragY (clamped, but 300 > screenMargin + loupeRadius = 170)
        assertThat(result.y).isCloseTo(300f, FLOAT_OFFSET)
    }

    @Test
    fun leftPlacement_clampsYToTopEdge() {
        // Finger at very top and far right (no room above, room to left)
        val drag = Offset(540f, 100f)
        val result = computeLoupeCenter(drag, configPx, containerWidth)

        // Left placement: X = 540 - 200 - 150 = 190 (room check: 190 - 150 = 40 >= 20 ✓)
        assertThat(result.x).isCloseTo(190f, FLOAT_OFFSET)
        // Y clamped to screenMargin + loupeRadius = 20 + 150 = 170
        assertThat(result.y).isCloseTo(170f, FLOAT_OFFSET)
    }

    @Test
    fun rightPlacement_whenNoRoomAboveOrLeft() {
        // Finger near top-left corner: not enough room above AND not enough room on the left
        // For left to fail: leftCenterX - loupeRadius < screenMargin
        // leftCenterX = dragX - 200 - 150 = dragX - 350
        // leftCenterX - 150 = dragX - 500 < 20 => dragX < 520
        // Also not enough room above: dragY < 520
        val drag = Offset(100f, 300f)
        val result = computeLoupeCenter(drag, configPx, containerWidth)

        // Right center X = 100 + 200 + 150 = 450
        // Clamped: min(450, 1080 - 20 - 150) = min(450, 910) = 450
        assertThat(result.x).isCloseTo(450f, FLOAT_OFFSET)
        // Y should equal dragY (300 > 170)
        assertThat(result.y).isCloseTo(300f, FLOAT_OFFSET)
    }

    @Test
    fun rightPlacement_clampsXToRightEdge() {
        // Use a narrow container to force right placement with X clamping
        val narrowResult = computeLoupeCenter(
            Offset(100f, 300f), configPx, 500f
        )

        // Right center X = 100 + 200 + 150 = 450
        // Clamped: min(450, 500 - 20 - 150) = min(450, 330) = 330
        assertThat(narrowResult.x).isCloseTo(330f, FLOAT_OFFSET)
        assertThat(narrowResult.y).isCloseTo(300f, FLOAT_OFFSET)
    }

    @Test
    fun rightPlacement_clampsYToTopEdge() {
        // Finger at extreme top-left: Y very small, no room above/left -> right, Y clamped
        val drag = Offset(50f, 50f)
        val result = computeLoupeCenter(drag, configPx, containerWidth)

        // above: 50 - 200 - 150 = -300, -300 - 150 = -450 < 20 ✗
        // left: 50 - 200 - 150 = -300, -300 - 150 = -450 < 20 ✗
        // right: 50 + 200 + 150 = 400
        assertThat(result.x).isCloseTo(400f, FLOAT_OFFSET)
        // Y clamped to screenMargin + loupeRadius = 170
        assertThat(result.y).isCloseTo(170f, FLOAT_OFFSET)
    }

    @Test
    fun abovePlacement_exactBoundary() {
        // dragY such that aboveCenterY - loupeRadius == screenMargin exactly
        // dragY - verticalOffset - loupeRadius - loupeRadius = screenMargin
        // dragY = screenMargin + 2*loupeRadius + verticalOffset = 20 + 300 + 200 = 520
        val drag = Offset(540f, 520f)
        val result = computeLoupeCenter(drag, configPx, containerWidth)

        // Should still place above (condition uses >=)
        val expectedY = 520f - 200f - 150f  // = 170
        assertThat(result.y).isCloseTo(expectedY, FLOAT_OFFSET)
        assertThat(result.x).isCloseTo(540f, FLOAT_OFFSET)
    }
}
