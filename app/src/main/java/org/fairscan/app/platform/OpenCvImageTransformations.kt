/*
 * Copyright 2025-2026 Pierre-Yves Nicolas
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
package org.fairscan.app.platform

import org.fairscan.app.data.ImageTransformations
import org.fairscan.imageprocessing.decodeJpeg
import org.fairscan.imageprocessing.encodeJpeg
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.min

class OpenCvTransformations : ImageTransformations {

    override fun rotate(
        input: ByteArray,
        rotationDegrees: Int,
        jpegQuality: Int
    ): ByteArray {
        return transform(input, jpegQuality) {
            org.fairscan.imageprocessing.rotate(it, rotationDegrees)
        }
    }

    override fun resize(input: ByteArray, maxSize: Int): ByteArray {
        return transform(input, 85) { src ->
            val ratio = min(maxSize.toFloat() / src.width(), maxSize.toFloat() / src.height())
            val newW = (src.width() * ratio).toDouble()
            val newH = (src.height() * ratio).toDouble()
            val scaled = Mat()
            Imgproc.resize(src, scaled, Size(newW, newH))
            scaled
        }
    }

    private fun transform(
        inBytes: ByteArray,
        jpegQuality: Int,
        transform: (Mat) -> Mat,
    ): ByteArray {
        val input = decodeJpeg(inBytes)
        var output: Mat? = null
        try {
            output = transform.invoke(input)
            return encodeJpeg(output, jpegQuality)
        } finally {
            input.release()
            output?.release()
        }
    }
}
