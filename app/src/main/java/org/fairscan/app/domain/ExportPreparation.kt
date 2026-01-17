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
package org.fairscan.app.domain

import org.fairscan.app.data.ImageRepository
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.resizeForMaxPixels
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs

fun jpegsForExport(
    imageRepository: ImageRepository,
    exportQuality: ExportQuality
): Sequence<ByteArray> {

    val pages = imageRepository.pages().asSequence()
    return when (exportQuality) {
        ExportQuality.BALANCED -> pages.mapNotNull { imageRepository.jpegBytes(it.id) }

        ExportQuality.LOW -> pages.mapNotNull { page ->
            imageRepository.jpegBytes(page.id)?.let { jpeg ->
                resizeJpegBytesForMaxPixels(
                    jpegBytes = jpeg,
                    maxPixels = exportQuality.maxPixels.toDouble(),
                    jpegQuality = exportQuality.jpegQuality
                )
            }
        }

        ExportQuality.HIGH -> pages.mapNotNull { page ->
            val sourceJpegBytes = imageRepository.sourceJpegBytes(page.id)
            val pageMetadata = page.metadata
            val manualRotation = page.manualRotation
            if (sourceJpegBytes != null && pageMetadata != null)
                prepareJpegForHigh(sourceJpegBytes, pageMetadata, manualRotation, exportQuality)
            else
                imageRepository.jpegBytes(page.id)
        }
    }
}

fun resizeJpegBytesForMaxPixels(
    jpegBytes: ByteArray,
    maxPixels: Double,
    jpegQuality: Int
): ByteArray? {
    val decoded = decodeJpeg(jpegBytes)
    if (decoded == null)
        return null

    val resized = resizeForMaxPixels(decoded, maxPixels)
    val outJpegBytes = encodeJpeg(resized, jpegQuality)

    decoded.release()
    resized.release()
    return outJpegBytes
}

fun prepareJpegForHigh(
    sourceJpegBytes: ByteArray,
    pageMetadata: PageMetadata,
    manualRotation: Rotation,
    exportQuality: ExportQuality,
): ByteArray? {

    val decoded = decodeJpeg(sourceJpegBytes)
    if (decoded == null)
        return null

    val quad = pageMetadata.normalizedQuad.scaledTo(1,1,decoded.width(), decoded.height())
    val page = extractDocument(
        decoded,
        quad,
        pageMetadata.baseRotation.add(manualRotation).degrees,
        pageMetadata.isColored,
        exportQuality.maxPixels)
    val outJpegBytes = encodeJpeg(page, exportQuality.jpegQuality)

    decoded.release()
    page.release()
    return outJpegBytes
}

fun decodeJpeg(jpegBytes: ByteArray): Mat? {
    val src = MatOfByte(*jpegBytes)
    val decoded = Imgcodecs.imdecode(src, Imgcodecs.IMREAD_COLOR)
    src.release()
    if (decoded.empty()) {
        decoded.release()
        return null
    }
    return decoded
}

fun encodeJpeg(mat: Mat, jpegQuality: Int): ByteArray? {
    val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality.coerceIn(0, 100))
    val encoded = MatOfByte()
    val ok = Imgcodecs.imencode(".jpg", mat, encoded, params)
    params.release()

    if (!ok) {
        encoded.release()
        return null
    }

    val result = encoded.toArray()
    encoded.release()
    return result
}
