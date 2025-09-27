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
package org.fairscan.app

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import org.fairscan.app.data.DocumentMetadata
import org.fairscan.app.data.Page
import java.io.File

const val SCAN_DIR_NAME = "scanned_pages"
const val THUMBNAIL_DIR_NAME = "thumbnails"

class ImageRepository(
    appFilesDir: File,
    val transformations: ImageTransformations,
    private val thumbnailSizePx: Int,
) {

    private val scanDir: File = File(appFilesDir, SCAN_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private val thumbnailDir: File = File(appFilesDir, THUMBNAIL_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private val metadataFile = File(scanDir, "document.json")

    private var fileNames: MutableList<String> =
        loadFileNames()

    private fun loadFileNames(): MutableList<String> {
        val filesOnDisk: Set<String> = scanDir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()

        val metadataFiles: List<String>? = loadMetadata()
            ?.pages
            ?.map { it.file }

        return when {
            metadataFiles != null -> metadataFiles
                .filter { it in filesOnDisk }
                .toMutableList()
            else -> filesOnDisk
                .sorted()
                .toMutableList()
        }
    }

    private fun loadMetadata(): DocumentMetadata? =
        if (metadataFile.exists()) {
            runCatching {
                Json.decodeFromString<DocumentMetadata>(metadataFile.readText())
            }.getOrNull()
        } else null

    private fun saveMetadata() {
        val metadata = DocumentMetadata(version = 1, pages = fileNames.map { id -> Page(id) })
        metadataFile.writeText(Json.encodeToString(metadata))
    }

    fun imageIds(): ImmutableList<String> = fileNames.toImmutableList()

    fun add(bytes: ByteArray) {
        val fileName = "${System.currentTimeMillis()}.jpg"
        val file = File(scanDir, fileName)
        file.writeBytes(bytes)
        writeThumbnail(file)
        fileNames.add(fileName)
        saveMetadata()
    }

    val idRegex = Regex("([0-9]+)(-(90|180|270))?\\.jpg")

    fun rotate(id: String, clockwise: Boolean) {
        val originalFile = File(scanDir, id)
        if (!originalFile.exists()) {
            return
        }
        idRegex.matchEntire(id)?.let {
            val baseId = it.groupValues[1]
            val degrees = it.groupValues[3].ifEmpty { "0" }.toInt()
            val targetDegrees = (degrees + (if (clockwise) 90 else 270)) % 360
            val rotatedId = if (targetDegrees == 0) "$baseId.jpg" else "$baseId-$targetDegrees.jpg"
            val rotatedFile = File(scanDir, rotatedId)
            transformations.rotate(originalFile, rotatedFile, clockwise)
            if (rotatedFile.exists()) {
                val index = fileNames.indexOf(id)
                if (index >= 0) {
                    fileNames[index] = rotatedId
                    saveMetadata()
                }
                delete(id)
            }
        }
    }

    fun getContent(id: String): ByteArray? {
        val file = File(scanDir, id)
        if (file.exists()) {
            return file.readBytes()
        }
        return null
    }

    fun getThumbnail(id: String): ByteArray? {
        val thumbFile = getThumbnailFile(id)
        if (!thumbFile.exists()) {
            val originalFile = File(scanDir, id)
            if (!originalFile.exists()) return null
            writeThumbnail(originalFile)
        }
        return if (thumbFile.exists()) thumbFile.readBytes() else null
    }

    private fun writeThumbnail(originalFile: File) {
        val thumbFile = getThumbnailFile(originalFile.name)
        transformations.resize(originalFile, thumbFile, thumbnailSizePx)
    }

    private fun getThumbnailFile(id: String): File = File(thumbnailDir, id)

    fun movePage(id: String, newIndex: Int) {
        if (!fileNames.remove(id)) return
        val safeIndex = newIndex.coerceIn(0, fileNames.size)
        fileNames.add(safeIndex, id)
        saveMetadata()
    }

    fun delete(id: String) {
        File(scanDir, id).delete()
        getThumbnailFile(id).delete()
        fileNames.remove(id)
        saveMetadata()
    }

    fun clear() {
        fileNames.clear()
        thumbnailDir.listFiles()?.forEach {
            file -> file.delete()
        }
        scanDir.listFiles()?.forEach {
            file -> file.delete()
        }
        saveMetadata() // "empty" json file
    }
}
