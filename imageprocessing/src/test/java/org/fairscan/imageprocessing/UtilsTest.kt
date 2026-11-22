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
import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

class UtilsTest {

    init { nu.pattern.OpenCV.loadLocally() }

    @Test
    fun `chroma is euclidean distance from neutral`() {
        assertThat(singleChroma(128.0 + 0, 128.0 + 0)).isEqualTo(0.0)
        assertThat(singleChroma(128.0 + 2, 128.0 + 0)).isEqualTo(2.0)
        assertThat(singleChroma(128.0 - 2, 128.0 + 0)).isEqualTo(2.0)
        assertThat(singleChroma(128.0 + 0, 128.0 + 2)).isEqualTo(2.0)
        assertThat(singleChroma(128.0 + 3, 128.0 - 4)).isEqualTo(5.0)
    }

    private fun singleChroma(a: Double, b: Double): Double {
        val aMat = Mat(1, 1, CvType.CV_8UC1, Scalar(a))
        val bMat = Mat(1, 1, CvType.CV_8UC1, Scalar(b))
        val chroma = labChroma(aMat, bMat)
        val toReturn = chroma.get(0, 0)[0]
        releaseAll(aMat, bMat, chroma)
        return toReturn
    }
    @Test
    fun `percentile of a uniform image is its value`() {
        Mat(2, 2, CvType.CV_8UC1, Scalar(42.0)).use { channel ->
            assertThat(percentile(channel, 0.5)).isEqualTo(42.0)
            assertThat(percentile(channel, 0.9)).isEqualTo(42.0)
        }
    }

    @Test
    fun `percentile does not depend on the order of the pixels`() {
        row(10, 40, 30, 20).use { channel ->
            assertThat(percentile(channel, 0.25)).isEqualTo(10.0)
            assertThat(percentile(channel, 0.50)).isEqualTo(20.0)
            assertThat(percentile(channel, 0.75)).isEqualTo(30.0)
            assertThat(percentile(channel, 1.00)).isEqualTo(40.0)
        }
    }

    @Test
    fun `percentile is the first level with at least that share of pixels at or below`() {
        row(10, 10, 10, 40).use { channel ->
            assertThat(percentile(channel, 0.75)).isEqualTo(10.0) // 3 pixels out of 4
            assertThat(percentile(channel, 0.76)).isEqualTo(40.0)
        }
    }

    @Test
    fun `percentile handles the first and the last level`() {
        row(0, 255).use { channel ->
            assertThat(percentile(channel, 0.5)).isEqualTo(0.0)
            assertThat(percentile(channel, 1.0)).isEqualTo(255.0)
        }
    }

    @Test
    fun `percentile with a mask counts only the masked pixels`() {
        row(10, 20, 30, 40).use { channel ->
            row(0, 255, 255, 0).use { mask ->
                // Fails if the total is the size of the image instead of the masked pixels
                assertThat(percentile(channel, 0.5, mask)).isEqualTo(20.0)
                assertThat(percentile(channel, 1.0, mask)).isEqualTo(30.0)
            }
        }
    }

    @Test
    fun `percentile with a mask that selects no pixel returns 255`() {
        row(42, 42).use { channel ->
            Mat.zeros(1, 2, CvType.CV_8UC1).use { mask ->
                assertThat(percentile(channel, 0.5, mask)).isEqualTo(255.0)
            }
        }
    }

    @Test
    fun `percentile counts out-of-range float values in the total`() {
        // -5 and 300 are ignored by the histogram, but they are among the 4 pixels.
        // The retinex relies on this behavior.
        rowF(-5f, 10f, 300f, 20f).use { channel ->
            assertThat(percentile(channel, 0.5)).isEqualTo(20.0) // 2 pixels out of 4
            assertThat(percentile(channel, 0.75)).isEqualTo(255.0) // never reached
        }
    }

    private fun row(vararg data: Int): Mat {
        val mat = Mat(1, data.size, CvType.CV_8UC1)
        mat.put(0, 0, data.map { it.toByte() }.toByteArray())
        return mat
    }

    private fun rowF(vararg data: Float): Mat {
        val mat = Mat(1, data.size, CvType.CV_32FC1)
        mat.put(0, 0, data)
        return mat
    }
}
