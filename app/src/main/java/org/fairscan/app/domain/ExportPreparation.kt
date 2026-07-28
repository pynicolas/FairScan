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
package org.fairscan.app.domain

import org.fairscan.app.data.ImageRepository
import org.fairscan.app.platform.bitonalFromJpeg
import org.fairscan.app.platform.processedBitonalImage
import org.fairscan.app.platform.processedImage
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.EstimatedDimensions
import org.fairscan.imageprocessing.estimateRealDimensions
import org.fairscan.imageprocessing.resizeForMaxPixels
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.Mat

fun interface JpegProvider {
    suspend fun get(): Jpeg
}

fun interface BitonalProvider {
    suspend fun get(): Bitonal
}

data class PageToExport(
    val page: ScanPage,
    val jpeg: JpegProvider,
    // Set for black and white pages only. The PDF writer embeds it instead of the JPEG.
    val bitonal: BitonalProvider? = null,
    // What OCR reads when the embedded image is not a JPEG it can use.
    val ocrJpeg: JpegProvider = jpeg,
) {
    fun estimatedDimensions(): EstimatedDimensions? {
        val metadata = page.metadata
        if (metadata == null)
            return null
        val size = metadata.sourceSize
        if (size == null)
            return null
        val quad = metadata.normalizedQuad.scaledTo(1.0, 1.0, size.width, size.height)
        val realDimensions = estimateRealDimensions(
            quad, size.width.toInt(), size.height.toInt(), metadata.opticalMeasures
        ).snapToStandardFormat()
        return realDimensions.applyRotation(page.totalRotation())
    }
}

private fun EstimatedDimensions.applyRotation(rotation: Rotation): EstimatedDimensions {
    if ((rotation == Rotation.R90 || rotation == Rotation.R270)
        && this is EstimatedDimensions.Physical) {
        return EstimatedDimensions.Physical(heightMm, widthMm)
    }
    return this
}

suspend fun pagesToExport(
    imageRepository: ImageRepository,
    exportQuality: ExportQuality
): List<PageToExport> = imageRepository.pages().map { page ->
    if (page.colorMode == ColorMode.BLACK_AND_WHITE)
        bitonalPageToExport(page, imageRepository, exportQuality)
    else
        standardPageToExport(page, imageRepository, exportQuality)
}

private fun standardPageToExport(
    page: ScanPage,
    imageRepository: ImageRepository,
    exportQuality: ExportQuality,
): PageToExport = when (exportQuality) {
    ExportQuality.BALANCED -> PageToExport(page, jpeg = { jpeg(page, imageRepository) })

    ExportQuality.LOW -> PageToExport(page, jpeg = {
        resizeJpegBytesForMaxPixels(
            jpeg = jpeg(page, imageRepository),
            maxPixels = exportQuality.maxPixels.toDouble(),
            jpegQuality = exportQuality.jpegQuality
        )
    })

    ExportQuality.HIGH -> PageToExport(page, jpeg = {
        val source = imageRepository.source(page.id)
        val metadata = page.metadata
        val colorMode = page.colorMode
        if (source != null && metadata != null && colorMode != null) {
            val rotation = page.totalRotation()
            processedImage(source, metadata, rotation, colorMode, exportQuality)
        }
        else
            jpeg(page, imageRepository)
    })
}

// Only the PDF writer looks at the bitonal provider, JPEG export keeps the standard one.
private fun bitonalPageToExport(
    page: ScanPage,
    imageRepository: ImageRepository,
    exportQuality: ExportQuality,
): PageToExport = standardPageToExport(page, imageRepository, exportQuality).copy(
    // The stored page, not the one the export quality asks for: OCR does not benefit from the
    // higher resolution, and rendering it again would double the work for the page.
    ocrJpeg = { jpeg(page, imageRepository) },
    bitonal = {
        val source = imageRepository.source(page.id)
        val metadata = page.metadata
        if (source != null && metadata != null) {
            processedBitonalImage(source, metadata, page.totalRotation(), exportQuality)
        }
        else
            bitonalFromJpeg(jpeg(page, imageRepository))
    },
)

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
