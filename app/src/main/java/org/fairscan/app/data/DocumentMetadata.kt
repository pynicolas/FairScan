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
package org.fairscan.app.data

import kotlinx.serialization.Serializable

@Serializable
data class DocumentMetadataV1(
    val version: Int = 1,
    val pages: List<PageV1>
)

@Serializable
data class PageV1(
    val file: String
)

@Serializable
data class DocumentMetadataV2(
    val version: Int = 2,
    val pages: List<PageV2>
)

@Serializable
data class PageV2(
    val id: String,
    val baseRotationDegrees: Int = 0,
    val manualRotationDegrees: Int = 0,
    val quad: NormalizedQuad? = null,
    val isColored: Boolean? = null
)

@Serializable
data class NormalizedQuad(
    val topLeft: PointD,
    val topRight: PointD,
    val bottomRight: PointD,
    val bottomLeft: PointD
)

@Serializable
data class PointD(
    val x: Double,
    val y: Double
)
