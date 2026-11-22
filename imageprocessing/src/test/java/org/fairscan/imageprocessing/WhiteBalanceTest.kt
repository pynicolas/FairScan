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
package org.fairscan.imageprocessing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.fairscan.imageprocessing.enhancement.SyntheticPage
import org.fairscan.imageprocessing.enhancement.saveDebugImage
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar

class WhiteBalanceTest {

    init {
        nu.pattern.OpenCV.loadLocally()
    }

    // The analysis does not depend on the resolution.
    // 300 px is small enough to be fast and big enough to understand a debug image.
    private val size = 300

    @Test
    fun `neutral page is left unchanged`() {
        val input = SyntheticPage(size, size).textLines().build(noiseSigma = 4.0)
        val analysis = analyzeWhiteBalance(input)
        assertThat(analysis.gains.maxChange).isLessThan(0.01)
        input.release()
    }

    @Test
    fun `warm light on white paper is neutralized`() =
        assertPaperBecomesNeutral("warm_light", b = 0.80, g = 0.95, r = 1.10)

    @Test
    fun `cold light on white paper is neutralized`() =
        assertPaperBecomesNeutral("cold_light", b = 1.10, g = 1.00, r = 0.80)

    @Test
    fun `colored flat areas on the page do not prevent the correction`() =
        assertPaperBecomesNeutral("flyer_warm_light", b = 0.80, g = 0.95, r = 1.10, lines = 2) {
            rect(Rect(20, 150, 120, 60), Scalar(40.0, 40.0, 200.0)) // red
            rect(Rect(160, 150, 120, 60), Scalar(200.0, 80.0, 40.0)) // blue
            rect(Rect(20, 230, 120, 50), Scalar(150.0, 60.0, 220.0)) // pink
            rect(Rect(160, 230, 120, 50), Scalar(60.0, 200.0, 230.0)) // yellow
        }

    @Test
    fun `colorful cover without white is left alone`() {
        // Four big flat colors, none of them is paper. The cast needed to make the
        // brightest one neutral is beyond the limit of the gains: it stays colored,
        // and the correction is cancelled.
        val half = size / 2
        val page = SyntheticPage(size, size)
        page.rect(Rect(0, 0, half, half), Scalar(50.0, 210.0, 240.0)) // yellow
        page.rect(Rect(half, 0, half, half), Scalar(40.0, 140.0, 230.0)) // orange
        page.rect(Rect(0, half, half, half), Scalar(30.0, 30.0, 200.0)) // red
        page.rect(Rect(half, half, half, half), Scalar(200.0, 100.0, 40.0)) // blue
        val input = page.build(noiseSigma = 4.0)

        val analysis = analyzeWhiteBalance(input)

        val output = analysis.gains.applyTo(input)
        saveDebugImage("colorful_cover", input, output)

        assertThat(analysis.topChroma)
            .describedAs("brightest pixels after the full correction")
            .isGreaterThan(6.0)
        assertThat(analysis.strength).isEqualTo(0.0)
        assertThat(analysis.gains.isIdentity).isTrue()
        releaseAll(input, output)
    }

    @Test
    fun `bright object outside the document mask is ignored`() {
        // Right half: something brighter than the paper, with its own color
        val input = SyntheticPage(size, size)
            .rect(Rect(size / 2, 0, size / 2, size), Scalar(200.0, 255.0, 255.0))
            .build()
        val docMask = Mat.zeros(size, size, CvType.CV_8UC1)
        docMask.submat(Rect(0, 0, size / 2, size)).use { it.setTo(Scalar(255.0)) }

        // Without the mask, the object is taken for the paper (this is what makes the test useful)
        assertThat(analyzeWhiteBalance(input).gains.maxChange).isGreaterThan(0.1)
        assertThat(analyzeWhiteBalance(input, docMask).gains.maxChange).isLessThan(0.01)

        releaseAll(input, docMask)
    }

    @Test
    fun `empty mask gives no correction`() {
        // A page that would be corrected: white paper under a warm light
        val input = SyntheticPage(size, size).illuminant(0.80, 0.95, 1.10).build()
        val docMask = Mat.zeros(size, size, CvType.CV_8UC1)

        // Without the mask, the page is corrected
        assertThat(analyzeWhiteBalance(input).gains.maxChange).isGreaterThan(0.1)

        val analysis = analyzeWhiteBalance(input, docMask)
        assertThat(analysis.gains.isIdentity).isTrue()
        assertThat(analysis.strength).isEqualTo(0.0)

        releaseAll(input, docMask)
    }

    // Colored light on a white page, with dark text:
    // after the correction, the paper must have no dominant channel
    private fun assertPaperBecomesNeutral(
        name: String, b: Double, g: Double, r: Double, lines: Int = 5,
        decorate: SyntheticPage.() -> Unit = {},
    ) {
        val page = SyntheticPage(size, size).illuminant(b, g, r).textLines(lines)
        page.decorate()
        val input = page.build(noiseSigma = 4.0)

        val analysis = analyzeWhiteBalance(input)
        val output = analysis.gains.applyTo(input)
        saveDebugImage(name, input, output)

        val paper = Rect(0, 0, size, 30) // above the first line of text
        assertThat(channelSpread(input, paper)).describedAs("cast before").isGreaterThan(30.0)
        assertThat(analysis.strength).describedAs("strength").isGreaterThan(0.99)
        assertThat(channelSpread(output, paper)).describedAs("cast after").isLessThan(3.0)

        releaseAll(input, output)
    }

    // Lines of dark "text", like a real page: the estimate of the paper must not depend on them
    private fun SyntheticPage.textLines(count: Int = 5) = apply {
        for (line in 0 until count) {
            for (n in 0 until 15) {
                rect(Rect(20 + n * 18, 40 + line * 50, 3, 20), level = 20)
            }
        }
    }

    // Difference between the strongest and the weakest channel of the average color of the area:
    // about 0 for a neutral color, large under a colored light
    private fun channelSpread(img: Mat, area: Rect): Double {
        val region = img.submat(area)
        val mean = Core.mean(region).`val`
        region.release()
        val channels = mean.take(3)
        return channels.max() - channels.min()
    }
}

class WhiteBalanceFunctionsTest {

    init {
        nu.pattern.OpenCV.loadLocally()
    }

    @Test
    fun `gains apply to the right channels`() {
        Mat(1, 1, CvType.CV_8UC3, Scalar(100.0, 100.0, 100.0)).use { pixel ->
            ChannelGains(1.5, 1.0, 0.5).applyTo(pixel).use { output ->
                // BGR order
                assertThat(output.get(0, 0).toList()).containsExactly(150.0, 100.0, 50.0)
            }
        }
    }

    @Test
    fun `gains are clamped`() {
        val gains = gainsFor(PaperColor(50.0, 200.0, 200.0))
        assertThat(gains.b).isCloseTo(1.33, within(1e-9))
        assertThat(gains.g).isCloseTo(0.75, within(1e-9))
        assertThat(gains.r).isCloseTo(0.75, within(1e-9))
    }

    @Test
    fun `strength scales the correction`() {
        val paper = PaperColor(180.0, 214.0, 248.0)
        val full = gainsFor(paper)
        val half = gainsFor(paper, strength = 0.5)
        assertThat(half.b).isCloseTo(1.0 + (full.b - 1.0) / 2, within(1e-9))
        assertThat(half.g).isCloseTo(1.0 + (full.g - 1.0) / 2, within(1e-9))
        assertThat(half.r).isCloseTo(1.0 + (full.r - 1.0) / 2, within(1e-9))
    }

    @Test
    fun `paper too dark is not corrected`() {
        assertThat(gainsFor(PaperColor(20.0, 30.0, 40.0)).isIdentity).isTrue()
    }

    @Test
    fun `ramp goes from full to none`() {
        assertThat(ramp(0.0)).isEqualTo(1.0)
        assertThat(ramp(4.0)).isEqualTo(1.0)
        assertThat(ramp(5.0)).isCloseTo(0.5, within(1e-9))
        assertThat(ramp(6.0)).isEqualTo(0.0)
        assertThat(ramp(50.0)).isEqualTo(0.0)
    }
}