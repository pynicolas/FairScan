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
package org.fairscan.imageprocessing

data class OcrTextBox(
    val text: String,
    val box: ImageRect,
    val lineHeight: Int,
    val lineBottom: Int,
)

data class ImageRect(
    val left: Int,
    val top: Int,
    val right: Int, // coordinate of the right side (from the left of the image)
    val bottom: Int, // coordinate of the bottom side (from the top of the image)
) {
    val width get() = right - left
    val height get() = bottom - top
}

data class PdfRect(
    val x: Float, // in points, from left side
    val y: Float, // in points, from bottom side (PDF convention)
    val width: Float,
    val height: Float
)

class OcrCoordinateConverter(
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val pageWidth: Float,   // in PDF points
    private val pageHeight: Float   // in PDF points
) {
    fun convert(rect: ImageRect): PdfRect {
        val scaleX = pageWidth / imageWidth
        val scaleY = pageHeight / imageHeight

        val x = rect.left * scaleX
        val y = pageHeight - (rect.bottom * scaleY)  // Y axis is inverted
        val width = rect.width * scaleX
        val height = rect.height * scaleY

        return PdfRect(x, y, width, height)
    }
}
