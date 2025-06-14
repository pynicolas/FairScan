/*
 * Copyright 2025 Pierre-Yves Nicolas
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
package org.mydomain.postprocessing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class PostProcessingTest {

    @Test
    fun `basic call to RawImage constructor`() {
        val image = RawImage(3, 2, IntArray(6))
        assertThat(image.width).isEqualTo(3)
        assertThat(image.height).isEqualTo(2)
    }

    @Test
    fun `RawImage constructor should detect inconsistency in dimensions`() {
        assertThatThrownBy { RawImage(3, 2, IntArray(5)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun grayscale() {
        val original = RawImage(1, 1, intArrayOf(0x00102030))
        val grayscale = original.toGrayscale()
        assertThat(grayscale.pixels).hasSize(1)
        assertThat(grayscale.pixels[0].toString(16)).isEqualTo("1c1c1c")
        // grayscale conversion formula (applied to 0x00102030)
        assertThat((0.3*1*16 + 0.59*2*16 + 0.11*3*16).toInt().toString(16)).isEqualTo("1c")
    }

    @Test
    fun `detect color image`() {
        val pink = 0x00FF00FF
        val gray = 0x00505050

        val grayImage = RawImage(1, 1, intArrayOf(gray))
        val pinkImage = RawImage(1, 1, intArrayOf(pink))

        assertThat(grayImage.isColorImage()).isFalse()
        assertThat(pinkImage.isColorImage()).isTrue()
    }

    @Test
    fun `correctBackgroundWithBlur should flatten gradient`() {
        // Image 5x1 with gradient (50 → 90)
        val width = 5
        val height = 1
        val originalPixels = intArrayOf(
            0x323232, // 50
            0x3C3C3C, // 60
            0x464646, // 70
            0x505050, // 80
            0x5A5A5A  // 90
        )
        val image = RawImage(width, height, originalPixels)
        val corrected = image.correctBackgroundWithBlur(radius = 1)
        val grays = corrected.pixels.map { it and 0xFF }

        val min = grays.minOrNull() ?: error("Empty result")
        val max = grays.maxOrNull() ?: error("Empty result")
        println("Corrected grayscale values: $grays")

        assertThat(max - min).isLessThan(15)
        assertThat(grays.average()).isGreaterThan(120.0).isLessThan(135.0)
    }


    @Test
    fun `run post processing on sample files`() {
        val inputDir = File("src/test/resources/cropped")
        val outputDir = File("build/processed_images")
        outputDir.mkdirs()
        outputDir.listFiles()?.forEach { f -> f.delete() }
        val inputFiles = inputDir.listFiles()
        assertThat(inputFiles).isNotNull.isNotEmpty
        inputFiles!!.forEach { inputFile ->
            println(inputFile)
            val inputImage = RawImage.fromBufferedImage(ImageIO.read(inputFile))
            val outputImage = postProcessDocument(inputImage)
            val outputFile = File(outputDir, inputFile.name)
            ImageIO.write(outputImage.toBufferedImage(), "jpg", outputFile)
        }
    }

}