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
import org.junit.Test

class QuadCoordinateUtilsTest {

    @Test
    fun calculateDisplaySize_scalesCorrectlyForVariousAspectRatios() {
        // Wider image than container - fits to width
        var result = QuadCoordinateUtils.calculateDisplaySize(1920, 1080, IntSize(1000, 800))
        assertThat(result.width).isEqualTo(1000)
        assertThat(result.height).isCloseTo(562, AssertJOffset.offset(1))

        // Taller image than container - fits to height
        result = QuadCoordinateUtils.calculateDisplaySize(1080, 1920, IntSize(1000, 800))
        assertThat(result.height).isEqualTo(800)
        assertThat(result.width).isCloseTo(450, AssertJOffset.offset(1))

        // Square image in square container
        result = QuadCoordinateUtils.calculateDisplaySize(500, 500, IntSize(1000, 1000))
        assertThat(result.width).isEqualTo(1000)
        assertThat(result.height).isEqualTo(1000)

        // Same aspect ratio - fills container
        result = QuadCoordinateUtils.calculateDisplaySize(800, 600, IntSize(400, 300))
        assertThat(result.width).isEqualTo(400)
        assertThat(result.height).isEqualTo(300)
    }

    @Test
    fun normalizedToScreen_convertsPointsWithCorrectOffset() {
        val containerSize = IntSize(1000, 800)
        val displaySize = IntSize(800, 600)
        // Offset: (1000-800)/2 = 100 for x, (800-600)/2 = 100 for y

        // Top-left corner (0,0)
        var result = QuadCoordinateUtils.normalizedToScreen(Point(0.0, 0.0), containerSize, displaySize)
        assertThat(result.x).isCloseTo(100f, AssertJOffset.offset(0.1f))
        assertThat(result.y).isCloseTo(100f, AssertJOffset.offset(0.1f))

        // Center (0.5, 0.5) -> x: 0.5*800+100=500, y: 0.5*600+100=400
        result = QuadCoordinateUtils.normalizedToScreen(Point(0.5, 0.5), containerSize, displaySize)
        assertThat(result.x).isCloseTo(500f, AssertJOffset.offset(0.1f))
        assertThat(result.y).isCloseTo(400f, AssertJOffset.offset(0.1f))

        // Bottom-right corner (1,1) -> x: 1.0*800+100=900, y: 1.0*600+100=700
        result = QuadCoordinateUtils.normalizedToScreen(Point(1.0, 1.0), containerSize, displaySize)
        assertThat(result.x).isCloseTo(900f, AssertJOffset.offset(0.1f))
        assertThat(result.y).isCloseTo(700f, AssertJOffset.offset(0.1f))

        // No offset when sizes match
        result = QuadCoordinateUtils.normalizedToScreen(Point(0.0, 0.0), IntSize(800, 600), IntSize(800, 600))
        assertThat(result.x).isCloseTo(0f, AssertJOffset.offset(0.1f))
        assertThat(result.y).isCloseTo(0f, AssertJOffset.offset(0.1f))
    }

    @Test
    fun screenDeltaToNormalized_convertsDeltas() {
        val displaySize = IntSize(800, 600)

        // Positive delta: 80/800=0.1, 60/600=0.1
        var result = QuadCoordinateUtils.screenDeltaToNormalized(Offset(80f, 60f), displaySize)
        assertThat(result.x).isCloseTo(0.1f, AssertJOffset.offset(0.001f))
        assertThat(result.y).isCloseTo(0.1f, AssertJOffset.offset(0.001f))

        // Zero delta
        result = QuadCoordinateUtils.screenDeltaToNormalized(Offset(0f, 0f), displaySize)
        assertThat(result.x).isEqualTo(0f)
        assertThat(result.y).isEqualTo(0f)

        // Negative delta: -160/800=-0.2, -120/600=-0.2
        result = QuadCoordinateUtils.screenDeltaToNormalized(Offset(-160f, -120f), displaySize)
        assertThat(result.x).isCloseTo(-0.2f, AssertJOffset.offset(0.001f))
        assertThat(result.y).isCloseTo(-0.2f, AssertJOffset.offset(0.001f))
    }

    @Test
    fun getImageOffset_calculatesCorrectOffsets() {
        // Standard offset
        var result = QuadCoordinateUtils.getImageOffset(IntSize(1000, 800), IntSize(800, 600))
        assertThat(result.width).isEqualTo(100)
        assertThat(result.height).isEqualTo(100)

        // Same size - zero offset
        result = QuadCoordinateUtils.getImageOffset(IntSize(800, 600), IntSize(800, 600))
        assertThat(result.width).isEqualTo(0)
        assertThat(result.height).isEqualTo(0)

        // Asymmetric offset (only horizontal)
        result = QuadCoordinateUtils.getImageOffset(IntSize(1000, 600), IntSize(800, 600))
        assertThat(result.width).isEqualTo(100)
        assertThat(result.height).isEqualTo(0)
    }
}
