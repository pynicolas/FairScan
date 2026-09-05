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
import org.fairscan.app.platform.processedImage
import org.fairscan.imageprocessing.EstimatedDimensions
import org.fairscan.imageprocessing.estimateRealDimensions
import org.fairscan.imageprocessing.resizeForMaxPixels
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.Mat

fun interface ImageProvider {
    suspend fun get(): EncodedImage
}

data class PageToExport(
    val page: ScanPage,
    val image: ImageProvider,
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
): List<PageToExport> {

    val pages = imageRepository.pages()
    return when (exportQuality) {
        ExportQuality.BALANCED -> pages.map {
            PageToExport(it) { image(it, imageRepository) }
        }

        ExportQuality.LOW -> pages.map { page ->
            PageToExport(page) {
                resizeImageForMaxPixels(
                    image = image(page, imageRepository),
                    maxPixels = exportQuality.maxPixels.toDouble(),
                    jpegQuality = exportQuality.jpegQuality
                )
            }
        }

        ExportQuality.HIGH -> pages.map { page ->
            PageToExport(page) {
                val source = imageRepository.source(page.id)
                val metadata = page.metadata
                val colorMode = page.colorMode
                if (source != null && metadata != null && colorMode != null) {
                    val rotation = page.totalRotation()
                    processedImage(source, metadata, rotation, colorMode, exportQuality)
                }
                else
                    image(page, imageRepository)
            }
        }
    }
}

private suspend fun image(page: ScanPage, imageRepository: ImageRepository): EncodedImage {
    val key = page.key()
    return imageRepository.image(key)
        ?: throw IllegalArgumentException("Image not found for $key")
}

private fun resizeImageForMaxPixels(
    image: EncodedImage,
    maxPixels: Double,
    jpegQuality: Int
): EncodedImage {
    var decoded: Mat? = null
    var resized: Mat? = null
    try {
        decoded = image.toMat()
        resized = resizeForMaxPixels(decoded, maxPixels)
        return when (image) {
            is Jpeg -> Jpeg.fromMat(resized, jpegQuality)
            is Png -> Png.fromMat(resized)
        }
    } finally {
        decoded?.release()
        resized?.release()
    }
}
