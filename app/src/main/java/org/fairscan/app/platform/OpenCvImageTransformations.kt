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
import org.fairscan.imageprocessing.encodeJpeg
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.min

class OpenCvTransformations : ImageTransformations {

    override fun rotate(
        inputFile: File,
        outputFile: File,
        rotationDegrees: Int,
        jpegQuality: Int
    ) {
        transform(inputFile, outputFile, jpegQuality) {
            org.fairscan.imageprocessing.rotate(it, rotationDegrees)
        }
    }

    override fun resize(inputFile: File, outputFile: File, maxSize: Int) {
        transform(inputFile, outputFile, 85) { src ->
            val ratio = min(maxSize.toFloat() / src.width(), maxSize.toFloat() / src.height())
            val newW = (src.width() * ratio).toDouble()
            val newH = (src.height() * ratio).toDouble()
            val scaled = Mat()
            Imgproc.resize(src, scaled, Size(newW, newH))
            scaled
        }
    }

    private fun transform(
        inputFile: File,
        outputFile: File,
        jpegQuality: Int,
        transform: (Mat) -> Mat,
    ) {
        val input = Imgcodecs.imread(inputFile.absolutePath)
        var output: Mat? = null
        try {
            require(!input.empty()) { "Could not load image from ${inputFile.absolutePath}" }
            output = transform.invoke(input)
            val outputBytes = encodeJpeg(output, jpegQuality)
            outputFile.writeBytes(outputBytes)
        } finally {
            input.release()
            output?.release()
        }
    }
}
