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
package org.fairscan.app.platform

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.domain.Bitonal
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.bitonalMaxPixels
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.screens.settings.DefaultColorMode
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.Mask
import org.fairscan.imageprocessing.OpticalMeasures
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.autoColorMode
import org.fairscan.imageprocessing.createQuad
import org.fairscan.imageprocessing.estimateRealDimensions
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.packBitsMsbFirst
import org.fairscan.imageprocessing.resizeForMaxPixels
import org.fairscan.imageprocessing.rotate
import org.fairscan.imageprocessing.scaledTo
import org.opencv.android.Utils
import org.opencv.core.CvException
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.min

class ImageProcessor(private val thumbnailSizePx: Int) : ImageTransformations {

    override fun rotate(input: Jpeg, rotationDegrees: Int): Jpeg {
        return transform(input, ExportQuality.BALANCED.jpegQuality) {
            rotate(it, rotationDegrees)
        }
    }

    override fun resizeToThumbnail(input: Jpeg): Jpeg {
        val maxSize = thumbnailSizePx.toFloat()
        return transform(input, 85) { src ->
            val ratio = min(maxSize / src.width(), maxSize / src.height())
            val newW = (src.width() * ratio).toDouble()
            val newH = (src.height() * ratio).toDouble()
            val scaled = Mat()
            try {
                Imgproc.resize(src, scaled, Size(newW, newH))
            } catch (e: CvException) {
                val msg = "Resize failed. src=${src.width()}x${src.height()} dst=${newW}x${newH}"
                throw IllegalStateException(msg, e)
            }
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

    override fun process(
        source: Jpeg,
        metadata: PageMetadata,
        colorMode: ColorMode
    ): Jpeg {
        val baseRotation = metadata.baseRotation
        return processedImage(source, metadata, baseRotation, colorMode, ExportQuality.BALANCED)
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
        page = renderPage(sourceMat, quad, rotationDegrees, colorMode, exportQuality,
            metadata.opticalMeasures)
        return Jpeg.fromMat(page, storedJpegQuality(colorMode, exportQuality))
    } finally {
        sourceMat?.release()
        page?.release()
    }
}

// A scaled down bitonal page is nothing but hard edges, which is exactly where JPEG rings.
private const val BITONAL_JPEG_QUALITY = 92

private fun storedJpegQuality(colorMode: ColorMode, exportQuality: ExportQuality) =
    if (colorMode == ColorMode.BLACK_AND_WHITE) BITONAL_JPEG_QUALITY
    else exportQuality.jpegQuality

private fun bitonalMaxPixels(
    source: Mat,
    quad: Quad,
    exportQuality: ExportQuality,
    opticalMeasures: OpticalMeasures?,
): Long = exportQuality.bitonalMaxPixels(
    estimateRealDimensions(quad, source.cols(), source.rows(), opticalMeasures)
        .snapToStandardFormat()
)

// Black and white is binarized at the export resolution and scaled down afterwards: at preview
// resolution a speck of glare merges with a glyph and can no longer be told apart from it.
private fun renderPage(
    source: Mat,
    quad: Quad,
    rotationDegrees: Int,
    colorMode: ColorMode,
    exportQuality: ExportQuality,
    opticalMeasures: OpticalMeasures?,
): Mat {
    if (colorMode != ColorMode.BLACK_AND_WHITE) {
        return extractDocument(source, quad, rotationDegrees, colorMode,
            exportQuality.maxPixels, opticalMeasures)
    }
    val full = extractDocument(source, quad, rotationDegrees, colorMode,
        bitonalMaxPixels(source, quad, exportQuality, opticalMeasures), opticalMeasures,
        allowUpscaling = true)
    return try {
        resizeForMaxPixels(full, exportQuality.maxPixels.toDouble())
    } finally {
        full.release()
    }
}

// Rebuilt from the original capture, because the stored page is a JPEG and would carry its
// compression artifacts into the PDF.
fun processedBitonalImage(
    source: Jpeg,
    metadata: PageMetadata,
    rotation: Rotation,
    exportQuality: ExportQuality,
): Bitonal {
    var sourceMat: Mat? = null
    var page: Mat? = null
    var gray: Mat? = null
    try {
        sourceMat = source.toMat()
        val quad = metadata.normalizedQuad.scaledTo(1, 1, sourceMat.width(), sourceMat.height())
        page = extractDocument(sourceMat, quad, rotation.degrees, ColorMode.BLACK_AND_WHITE,
            bitonalMaxPixels(sourceMat, quad, exportQuality, metadata.opticalMeasures),
            metadata.opticalMeasures, allowUpscaling = true)
        gray = Mat()
        Imgproc.cvtColor(page, gray, Imgproc.COLOR_BGR2GRAY)
        return packBitonal(gray)
    } finally {
        sourceMat?.release()
        page?.release()
        gray?.release()
    }
}

// Fallback for pages whose original capture is no longer available.
fun bitonalFromJpeg(jpeg: Jpeg): Bitonal {
    var mat: Mat? = null
    var gray: Mat? = null
    try {
        mat = jpeg.toMat()
        gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        return packBitonal(gray)
    } finally {
        mat?.release()
        gray?.release()
    }
}

private fun packBitonal(gray: Mat): Bitonal {
    val width = gray.width()
    val height = gray.height()
    val pixels = ByteArray(width * height)
    gray.get(0, 0, pixels)
    return Bitonal(width, height, packBitsMsbFirst(pixels, width, height))
}

fun extractDocumentFromBitmap(
    source: Bitmap,
    quadInMask: Quad?,
    rotationDegrees: Int,
    mask: Mask?,
    viewModelScope: CoroutineScope,
    defaultColorMode: DefaultColorMode = DefaultColorMode.AUTO,
    opticalMeasures: OpticalMeasures?,
): CapturedPage {
    val exportQuality = ExportQuality.BALANCED
    var colorMode = ColorMode.COLOR
    var autoColorMode = colorMode
    var normalizedQuad = createQuad(listOf(
        Point(0.0, 0.0), Point(0.0, 1.0), Point(1.0, 1.0), Point(1.0, 0.0))
    )
    var page: Mat

    val rgba = Mat()
    Utils.bitmapToMat(source, rgba)
    val bgr = Mat()
    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
    rgba.release()

    if (mask == null || quadInMask == null) {
        // No document detected
        val resized = resizeForMaxPixels(bgr, exportQuality.maxPixels.toDouble())
        page = rotate(resized, rotationDegrees)
        resized.release()
    } else {
        val quad = quadInMask.scaledTo(mask.width, mask.height, source.width, source.height)
        normalizedQuad = quad.scaledTo(source.width, source.height, 1, 1)
        autoColorMode = autoColorMode(bgr, mask, quad)
        colorMode = defaultColorMode.colorMode ?: autoColorMode
        page = renderPage(bgr, quad, rotationDegrees, colorMode, exportQuality, opticalMeasures)
    }

    val pageJpeg = Jpeg.fromMat(page, storedJpegQuality(colorMode, exportQuality))
    bgr.release()
    page.release()

    val baseRotation = Rotation.fromDegrees(rotationDegrees)
    val sourceSize = ImageSize(source.width, source.height)
    val metadata =
        PageMetadata(normalizedQuad, baseRotation, autoColorMode, sourceSize, opticalMeasures)
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
