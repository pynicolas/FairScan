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
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.min

class OpenCvTransformations : ImageTransformations {

    override fun rotate(
        input: Jpeg,
        rotationDegrees: Int,
        jpegQuality: Int
    ): Jpeg {
        return transform(input, jpegQuality) {
            org.fairscan.imageprocessing.rotate(it, rotationDegrees)
        }
    }

    override fun resize(input: Jpeg, maxSize: Int): Jpeg {
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
        inJpeg: Jpeg,
        jpegQuality: Int,
        transform: (Mat) -> Mat,
    ): Jpeg {
        val input = inJpeg.toMat()
        var output: Mat? = null
        try {
            output = transform.invoke(input)
            return Jpeg.fromMat(output, jpegQuality)
        } finally {
            input.release()
            output?.release()
        }
    }

    override fun process(source: Jpeg, metadata: PageMetadata, colorMode: ColorMode): Jpeg {
        val exportQuality = ExportQuality.BALANCED
        var sourceMat: Mat? = null
        var page: Mat? = null
        try {
            sourceMat = source.toMat()
            val quad = metadata.normalizedQuad.scaledTo(
                1,
                1,
                sourceMat.width(),
                sourceMat.height()
            )
            page = extractDocument(
                sourceMat,
                quad,
                metadata.baseRotation.degrees,
                colorMode,
                exportQuality.maxPixels
            )
            return Jpeg.fromMat(page, exportQuality.jpegQuality)
        } finally {
            sourceMat?.release()
            page?.release()
        }
    }
}
