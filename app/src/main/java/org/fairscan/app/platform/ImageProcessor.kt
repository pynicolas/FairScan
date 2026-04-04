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

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.screens.settings.DefaultColorMode
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.Mask
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.autoColorMode
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.scaledTo
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.min

class ImageProcessor(private val thumbnailSizePx: Int) : ImageTransformations {

    override fun rotate(input: Jpeg, rotationDegrees: Int): Jpeg {
        return transform(input, ExportQuality.BALANCED.jpegQuality) {
            org.fairscan.imageprocessing.rotate(it, rotationDegrees)
        }
    }

    override fun resizeToThumbnail(input: Jpeg): Jpeg {
        val maxSize = thumbnailSizePx.toFloat()
        return transform(input, 85) { src ->
            val ratio = min(maxSize / src.width(), maxSize / src.height())
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
        return processedImage(source, metadata, metadata.baseRotation, colorMode, ExportQuality.BALANCED)
    }
}

fun processedImage(
    source: Jpeg,
    metadata: PageMetadata,
    rotation: Rotation,
    colorMode: ColorMode,
    exportQuality: ExportQuality,
): Jpeg {
    val rotationDegrees = rotation.degrees
    var sourceMat: Mat? = null
    var page: Mat? = null
    try {
        sourceMat = source.toMat()
        val quad = metadata.normalizedQuad.scaledTo(1, 1, sourceMat.width(), sourceMat.height())
        page = extractDocument(sourceMat, quad, rotationDegrees, colorMode, exportQuality.maxPixels)
        return Jpeg.fromMat(page, exportQuality.jpegQuality)
    } finally {
        sourceMat?.release()
        page?.release()
    }
}

fun extractDocumentFromBitmap(
    source: Bitmap,
    quadInMask: Quad,
    rotationDegrees: Int,
    mask: Mask,
    viewModelScope: CoroutineScope,
    defaultColorMode: DefaultColorMode = DefaultColorMode.AUTO
): CapturedPage {
    val exportQuality = ExportQuality.BALANCED
    val quad = quadInMask.scaledTo(mask.width, mask.height, source.width, source.height)

    val rgba = Mat()
    Utils.bitmapToMat(source, rgba)
    val bgr = Mat()
    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
    rgba.release()
    val autoColorMode = autoColorMode(bgr, mask, quad)
    val colorMode = defaultColorMode.colorMode ?: autoColorMode
    val page = extractDocument(bgr, quad, rotationDegrees, colorMode, exportQuality.maxPixels)
    val pageJpeg = Jpeg.fromMat(page, exportQuality.jpegQuality)
    bgr.release()
    page.release()

    val normalizedQuad = quad.scaledTo(source.width, source.height, 1, 1)
    val baseRotation = Rotation.fromDegrees(rotationDegrees)
    val metadata = PageMetadata(normalizedQuad, baseRotation, autoColorMode)
    val sourceJpegDeferred = viewModelScope.async(Dispatchers.IO) {
        compressSource(source)
    }
    return CapturedPage(pageJpeg, sourceJpegDeferred, metadata, colorMode)
}

private fun compressSource(source: Bitmap): Jpeg {
    val rgba = Mat()
    Utils.bitmapToMat(source, rgba)
    val bgr = Mat()
    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
    rgba.release()
    return try {
        Jpeg.fromMat(bgr, 90)
    } finally {
        bgr.release()
    }
}
