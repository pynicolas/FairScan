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
import org.fairscan.app.platform.processedImage
import org.fairscan.imageprocessing.resizeForMaxPixels
import org.opencv.core.Mat

fun interface JpegProvider {
    suspend fun get(): Jpeg
}

suspend fun jpegsForExport(
    imageRepository: ImageRepository,
    exportQuality: ExportQuality
): List<JpegProvider> {

    val pages = imageRepository.pages()
    return when (exportQuality) {
        ExportQuality.BALANCED -> pages.map {
            JpegProvider { jpeg(it, imageRepository) }
        }

        ExportQuality.LOW -> pages.map { page ->
            JpegProvider {
                resizeJpegBytesForMaxPixels(
                    jpeg = jpeg(page, imageRepository),
                    maxPixels = exportQuality.maxPixels.toDouble(),
                    jpegQuality = exportQuality.jpegQuality
                )
            }
        }

        ExportQuality.HIGH -> pages.map { page ->
            JpegProvider {
                val source = imageRepository.source(page.id)
                val metadata = page.metadata
                val manualRotation = page.manualRotation
                val colorMode = page.colorMode
                if (source != null && metadata != null && colorMode != null) {
                    val rotation = metadata.baseRotation.add(manualRotation)
                    processedImage(source, metadata, rotation, colorMode, exportQuality)
                }
                else
                    jpeg(page, imageRepository)
            }
        }
    }
}

private suspend fun jpeg(page: ScanPage, imageRepository: ImageRepository): Jpeg {
    val key = page.key()
    return imageRepository.jpegBytes(key)
        ?: throw IllegalArgumentException("JPEG not found for $key")
}

private fun resizeJpegBytesForMaxPixels(
    jpeg: Jpeg,
    maxPixels: Double,
    jpegQuality: Int
): Jpeg {
    var decoded: Mat? = null
    var resized: Mat? = null
    try {
        decoded = jpeg.toMat()
        resized = resizeForMaxPixels(decoded, maxPixels)
        return Jpeg.fromMat(resized, jpegQuality)
    } finally {
        decoded?.release()
        resized?.release()
    }
}
