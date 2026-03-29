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
package org.fairscan.app.domain

import org.fairscan.app.data.ImageRepository
import org.fairscan.imageprocessing.decodeJpeg
import org.fairscan.imageprocessing.encodeJpeg
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.resizeForMaxPixels
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.Mat

fun interface JpegProvider {
    suspend fun get(): ByteArray
}

suspend fun jpegsForExport(
    imageRepository: ImageRepository,
    exportQuality: ExportQuality
): List<JpegProvider> {

    val pages = imageRepository.pages()
    return when (exportQuality) {
        ExportQuality.BALANCED -> pages.map {
            JpegProvider { jpegBytes(it, imageRepository) }
        }

        ExportQuality.LOW -> pages.map { page ->
            JpegProvider {
                resizeJpegBytesForMaxPixels(
                    jpegBytes = jpegBytes(page, imageRepository),
                    maxPixels = exportQuality.maxPixels.toDouble(),
                    jpegQuality = exportQuality.jpegQuality
                )
            }
        }

        ExportQuality.HIGH -> pages.map { page ->
            JpegProvider {
                val sourceJpegBytes = imageRepository.sourceJpegBytes(page.id)
                val pageMetadata = page.metadata
                val manualRotation = page.manualRotation
                if (sourceJpegBytes != null && pageMetadata != null)
                    prepareJpegForHigh(sourceJpegBytes, pageMetadata, manualRotation, exportQuality)
                else
                    jpegBytes(page, imageRepository)
            }
        }
    }
}

private suspend fun jpegBytes(page: ScanPage, imageRepository: ImageRepository): ByteArray {
    val key = page.key()
    return imageRepository.jpegBytes(key)
        ?: throw IllegalArgumentException("JPEG not found for $key")
}

private fun resizeJpegBytesForMaxPixels(
    jpegBytes: ByteArray,
    maxPixels: Double,
    jpegQuality: Int
): ByteArray {
    var decoded: Mat? = null
    var resized: Mat? = null
    try {
        decoded = decodeJpeg(jpegBytes)
        resized = resizeForMaxPixels(decoded, maxPixels)
        return encodeJpeg(resized, jpegQuality)
    } finally {
        decoded?.release()
        resized?.release()
    }
}

private fun prepareJpegForHigh(
    sourceJpegBytes: ByteArray,
    pageMetadata: PageMetadata,
    manualRotation: Rotation,
    exportQuality: ExportQuality,
): ByteArray {

    var decoded: Mat? = null
    var page: Mat? = null
    try {
        decoded = decodeJpeg(sourceJpegBytes)
        val quad = pageMetadata.normalizedQuad.scaledTo(1, 1, decoded.width(), decoded.height())
        page = extractDocument(
            decoded,
            quad,
            pageMetadata.baseRotation.add(manualRotation).degrees,
            pageMetadata.isColored,
            exportQuality.maxPixels
        )
        return encodeJpeg(page, exportQuality.jpegQuality)
    } finally {
        decoded?.release()
        page?.release()
    }
}
