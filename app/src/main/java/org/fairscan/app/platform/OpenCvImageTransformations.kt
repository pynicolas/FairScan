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
import org.fairscan.app.domain.ExportQuality
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.encodeJpeg
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
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

    override fun extractDocument(
        inputFile: File,
        outputFile: File,
        normalizedQuad: Quad,
        rotationDegrees: Int,
        isColored: Boolean,
        quality: ExportQuality
    ) {
        // Load source image
        val src = Imgcodecs.imread(inputFile.absolutePath)
        require(!src.empty()) { "Could not load image from ${inputFile.absolutePath}" }

        var extracted: Mat? = null
        var params: MatOfInt? = null
        try {
            val quad = normalizedQuad.scaledTo(1, 1, src.width(), src.height())

            extracted = org.fairscan.imageprocessing.extractDocument(
                src,
                quad,
                rotationDegrees,
                isColored,
                quality.maxPixels
            )

            // Save result as JPEG
            params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality.jpegQuality)
            if (!Imgcodecs.imwrite(outputFile.absolutePath, extracted, params)) {
                throw RuntimeException("Could not write image to ${outputFile.absolutePath}")
            }
        } finally {
            params?.release()
            extracted?.release()
            src.release()
        }
    }
}
