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

import java.awt.image.BufferedImage

data class RawImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray // each Int represents a pixel as 0xAARRGGBB
) {

    init {
        require(pixels.size == width * height) { "Pixel array size does not match dimensions" }
    }

    companion object {
        fun fromBufferedImage(image: BufferedImage): RawImage {
            val width = image.width
            val height = image.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val rgb = image.getRGB(x, y)
                    pixels[y * width + x] = rgb
                }
            }
            return RawImage(width, height, pixels)
        }
    }

    fun toBufferedImage(): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                image.setRGB(x, y, pixels[index])
            }
        }
        return image
    }

    fun toGrayscale(): RawImage {
        val grayPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val rgb = pixels[i]
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF

            // See https://en.wikipedia.org/wiki/Grayscale#Converting_color_to_grayscale
            val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt().coerceIn(0, 255)

            grayPixels[i] = (gray shl 16) or (gray shl 8) or gray
        }
        return RawImage(width, height, grayPixels)
    }

    fun isColorImage(saturationThreshold: Double = 0.1): Boolean {
        var totalSaturation = 0.0
        for (rgb in pixels) {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF

            val max = maxOf(r, g, b) / 255.0
            val min = minOf(r, g, b) / 255.0

            val saturation = if (max == 0.0) 0.0 else (max - min) / max
            totalSaturation += saturation
        }

        val avgSaturation = totalSaturation / pixels.size
        println(avgSaturation)
        return avgSaturation > saturationThreshold
    }
}

fun postProcessDocument(original: RawImage): RawImage {
    return if (original.isColorImage()) original else original.toGrayscale()
}
