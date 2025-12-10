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
package org.fairscan.evaluation

import java.io.File

data class ImageMeta(
    val imgName: String,
    val docId: String
)

data class DocumentMeta(
    val docId: String,
    val isColored: Boolean
)

object CsvMetadata {

    fun readImagesCsv(file: File): List<ImageMeta> {
        return file.readLines()
            .drop(1) // skip header
            .map { line ->
                val cols = line.split(',')
                ImageMeta(
                    imgName = cols[0].trim(),
                    docId = cols[1].trim()
                )
            }
    }

    fun readDocumentsCsv(file: File): Map<String, DocumentMeta> {
        return file.readLines()
            .drop(1)
            .map { line ->
                val cols = line.split(',')
                val docId = cols[0].trim()
                val isColored = cols[1].trim().equals("TRUE", ignoreCase = true)
                DocumentMeta(docId, isColored)
            }
            .associateBy { it.docId }
    }
}
