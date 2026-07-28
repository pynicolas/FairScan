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

import org.fairscan.app.R
import org.fairscan.imageprocessing.EstimatedDimensions
import org.fairscan.imageprocessing.PaperFormats

// Black and white is sized by target resolution rather than by a pixel count: it is compressed
// losslessly, so its size follows the number of black-white transitions rather than the pixels,
// and a page only looks sharp in one bit per pixel at a high enough resolution. A pixel budget
// would also give a receipt a very different resolution than an A4 page.
enum class ExportQuality(
    val jpegQuality: Int,
    val maxPixels: Long,
    val bitonalDpi: Int,
    val labelResource: Int
) {
    LOW(
        jpegQuality = 60,
        maxPixels = 1_000_000,
        bitonalDpi = 150,
        R.string.export_quality_low,
    ),
    BALANCED(
        jpegQuality = 75,
        maxPixels = 2_000_000,
        bitonalDpi = 300,
        R.string.export_quality_balanced,
    ),
    HIGH(
        jpegQuality = 80,
        maxPixels = 4_000_000,
        bitonalDpi = 450,
        R.string.export_quality_high,
    )
}

// 450 dpi on A4 is 19.6 megapixels, which is the most the highest setting ever asks for.
private const val MAX_BITONAL_PIXELS = 20_000_000L

// Pixels needed to reach bitonalDpi on this page. Falls back to A4 when the physical size could
// not be estimated, the same assumption the PDF writer makes for the page box.
fun ExportQuality.bitonalMaxPixels(dimensions: EstimatedDimensions): Long {
    val widthMm: Double
    val heightMm: Double
    if (dimensions is EstimatedDimensions.Physical) {
        widthMm = dimensions.widthMm
        heightMm = dimensions.heightMm
    } else {
        widthMm = PaperFormats.A4.widthMm
        heightMm = PaperFormats.A4.heightMm
    }
    val pixels = (widthMm / 25.4 * bitonalDpi) * (heightMm / 25.4 * bitonalDpi)
    return pixels.toLong().coerceIn(maxPixels, MAX_BITONAL_PIXELS)
}
