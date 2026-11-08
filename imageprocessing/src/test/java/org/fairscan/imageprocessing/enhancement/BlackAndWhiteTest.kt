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
package org.fairscan.imageprocessing.enhancement

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.binarizeDocument
import org.fairscan.imageprocessing.flattenedGrayscale
import org.junit.Ignore
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.Rect
import org.opencv.core.Scalar

class BlackAndWhiteTest {

    init {
        nu.pattern.OpenCV.loadLocally()
    }

    @Test
    fun thin_black_strokes_keep_their_width_and_paper_stays_white() {
        val strokeWidth = 3
        val strokeHeight = 30
        val strokePitch = 25 // distance between two strokes of a line
        val linePitch = 150  // distance between two lines
        val lineCount = 8
        val strokesPerLine = 40
        val left = 100
        val firstLineTop = 200
        val edge = 4  // pixels beside an ink area that we do not judge
        val gapMargin = 5

        val page = SyntheticPage().lightFalloff(0.1)
        val lines = (0 until lineCount).map { line ->
            (0 until strokesPerLine).map { n ->
                Rect(left + n * strokePitch, firstLineTop + line * linePitch, strokeWidth, strokeHeight)
            }
        }
        lines.flatten().forEach { page.rect(it, level = 20) }
        val input = page.build(noiseSigma = 4.0)

        val output = binarizeDocument(input, ColorMode.COLOR)
        saveDebugImage("thin_strokes", input, output)

        assertThat(output.isStrictlyBinary()).isTrue()

        // Width: black pixels per row in a window centered on the stroke, which includes some
        // paper on each side. Rows near the ends (top and bottom) of the stroke are left out.
        val widths = lines.flatten().map {
            val window = Rect(it.x - edge, it.y + edge, strokeWidth + 2 * edge, strokeHeight - 2 * edge)
            output.blackRatio(window) * window.width
        }
        assertThat(widths.min()).describedAs("thinnest stroke").isGreaterThan(strokeWidth - 0.5)
        assertThat(widths.max()).describedAs("thickest stroke").isLessThan(strokeWidth + 0.5)

        // Paper between two strokes of the same line
        val between = lines.flatMap { line ->
            line.zipWithNext { a, b ->
                val area = Rect(a.x + strokeWidth + edge, a.y,
                    b.x - a.x - strokeWidth - 2 * edge, strokeHeight)
                output.blackRatio(area)
            }
        }
        assertThat(between.max()).describedAs("blackest space between strokes").isLessThan(0.005)

        // Paper between two lines
        for (line in 0 until lineCount - 1) {
            val top = lines[line][0].y + strokeHeight + gapMargin
            val gap = Rect(left, top, strokesPerLine * strokePitch, linePitch - strokeHeight - 2 * gapMargin)
            assertThat(output.blackRatio(gap)).describedAs("gap after line $line").isLessThan(0.005)
        }

        input.release(); output.release()
    }

    @Test
    fun medium_grey_band_stays_black_thanks_to_wide_sauvola_window() {
        // The test seems to pass until a height of 115 px for the grey area
        val greyArea = Rect(300, 600, 600, 50)
        val input = SyntheticPage()
            .lightFalloff(0.15)
            .rect(greyArea, level = 150)
            .build(noiseSigma = 4.0)

        val output = binarizeDocument(input, ColorMode.COLOR)
        saveDebugImage("medium_grey_band", input, output)

        assertThat(output.isStrictlyBinary()).isTrue()
        val paper = Rect(0, 0, 1200, 600)
        assertThat(output.blackRatio(paper)).describedAs("paper above grey area").isLessThan(0.001)
        val inside = greyArea.inset(3)
        assertThat(output.blackRatio(inside)).describedAs("inside of grey area").isGreaterThan(0.99)

        input.release(); output.release()
    }

    @Test
    fun large_dark_area_stays_black_up_to_its_center() {
        val darkArea = Rect(300, 600, 600, 500) // well above the Sauvola window (~170 px)
        val input = SyntheticPage()
            .lightFalloff(0.15)
            .rect(darkArea, level = 40)
            .build(noiseSigma = 3.0)

        val output = binarizeDocument(input, ColorMode.COLOR)
        saveDebugImage("large_dark_area", input, output)

        assertThat(output.isStrictlyBinary()).isTrue()
        val paper = Rect(50, 50, 1100, 400)
        assertThat(output.blackRatio(paper)).describedAs("paper").isLessThan(0.01)
        val inside = darkArea.inset(10)
        assertThat(output.blackRatio(inside)).describedAs("inside of dark area").isGreaterThan(0.99)

        input.release(); output.release()
    }

    @Test
    @Ignore("known bug: Retinex seems to damage the center of the rectangle")
    fun large_dark_area_stays_black_up_to_its_center_in_noisy_image() {
        val darkArea = Rect(300, 600, 600, 500) // well above the Sauvola window (~170 px)
        val input = SyntheticPage()
            .lightFalloff(0.15)
            .rect(darkArea, level = 40)
            .build(noiseSigma = 8.0)

        // This is only to understand why the test fails
        val flat = flattenedGrayscale(input)
        val center = Rect(darkArea.x + darkArea.width / 2 - 20, darkArea.y + darkArea.height / 2 - 20, 40, 40)
        println("flattened, mean at center = ${Core.mean(flat.submat(center)).`val`[0]}")
        saveDebugImage("large_dark_area_in_noisy_image_flattened", input, flat)

        val output = binarizeDocument(input, ColorMode.COLOR)
        saveDebugImage("large_dark_area_in_noisy_image", input, output)

        assertThat(output.isStrictlyBinary()).isTrue()
        val paper = Rect(50, 50, 1100, 400)
        assertThat(output.blackRatio(paper)).describedAs("paper").isLessThan(0.01)
        val inside = darkArea.inset(10)
        assertThat(output.blackRatio(inside)).describedAs("inside of dark area").isGreaterThan(0.99)

        input.release(); output.release()
    }

    @Test
    fun medium_colored_area_stays_black_up_to_its_center() {
        val blue = Scalar(160.0, 90.0, 30.0)
        val button = Rect(300, 600, 500, 300)

        val input = SyntheticPage()
            .lightFalloff(0.1)
            .rect(button, bgr = blue)
            .build(noiseSigma = 4.0)

        val output = binarizeDocument(input, ColorMode.COLOR)
        saveDebugImage("colored_area", input, output)

        assertThat(output.isStrictlyBinary()).isTrue()

        val paperAbove = Rect(button.x, button.y - 60, button.width, 40)
        assertThat(output.blackRatio(paperAbove)).describedAs("paper above").isLessThan(0.01)

        assertThat(output.blackRatio(button.inset(3))).describedAs("colored area").isGreaterThan(0.99)
        val center = Rect(button.x + button.width / 2 - 40, button.y + button.height / 2 - 40, 80, 80)
        assertThat(output.blackRatio(center)).describedAs("center of the area").isGreaterThan(0.99)

        input.release(); output.release()
    }

    private fun Rect.grow(margin: Int) = Rect(x - margin, y - margin, width + 2 * margin, height + 2 * margin)

    @Test
    fun small_light_dot_is_kept_next_to_a_letter_and_closed_when_alone() {
        // Light print on a dark fill. The fill is a bit larger than the Sauvola window, so that the
        // fill step is what protects it, but smaller than the retinex kernel (see the large area test).
        val darkArea = Rect(450, 600, 250, 250)
        val stem = Rect(480, 680, 4, 30)  // the body of an "i": clearly a letter
        val dotNearLetter = Rect(480, 674, 3, 3) // its dot, 3 px above the stem
        val lonelyDot = Rect(650, 780, 3, 3) // same size, far from any letter: glare / noise

        val input = SyntheticPage()
            .lightFalloff(0.1)
            .rect(darkArea, level = 40)
            .rect(stem, level = 220)
            .rect(dotNearLetter, level = 220)
            .rect(lonelyDot, level = 220)
            .build(noiseSigma = 3.0)

        val output = binarizeDocument(input, ColorMode.COLOR)
        saveDebugImage("despeckle", input, output)

        assertThat(output.isStrictlyBinary()).isTrue()

        // Sanity: the fill is black away from the details, and the letter is there
        assertThat(output.blackRatio(darkArea.inset(10))).describedAs("fill").isGreaterThan(0.99)
        assertThat(output.blackRatio(stem.inset(1))).describedAs("stem of the letter").isLessThan(0.1)

        // Content: the dot next to the letter stays white
        assertThat(output.blackRatio(dotNearLetter)).describedAs("dot next to a letter").isLessThan(0.5)

        // Noise: the same dot alone is closed, the surroundings are fully black
        assertThat(output.blackRatio(lonelyDot.grow(3))).describedAs("lonely dot").isGreaterThan(0.99)

        input.release(); output.release()
    }
}
