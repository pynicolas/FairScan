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

import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.OpticalMeasures
import org.fairscan.imageprocessing.Quad

data class PageMetadata(
    val normalizedQuad: Quad,
    val baseRotation: Rotation,
    val autoColorMode: ColorMode,
    val sourceSize: ImageSize?,
    val opticalMeasures: OpticalMeasures?,
)

data class ScanPage(
    val id: String,
    val manualRotation: Rotation,
    val colorMode: ColorMode?,
    val quadVersion: Int,
    val metadata: PageMetadata?,
) {
    fun key() = PageViewKey(id, manualRotation, colorMode, quadVersion)
    fun totalRotation() = manualRotation.add(metadata?.baseRotation ?: Rotation.R0)
}

data class PageViewKey(
    val pageId: String,
    val rotation: Rotation,
    val colorMode: ColorMode?,
    val quadVersion: Int,
)
enum class Rotation(val degrees: Int) {
    R0(0),
    R90(90),
    R180(180),
    R270(270);

    fun add(other: Rotation): Rotation =
        fromDegrees((degrees + other.degrees) % 360)

    companion object {
        fun fromDegrees(deg: Int): Rotation =
            entries.first { it.degrees == ((deg % 360 + 360) % 360) }
    }
}
