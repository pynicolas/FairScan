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
package org.fairscan.app.data

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.Json
import org.fairscan.app.domain.PageMetadata
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import java.io.File

const val SOURCE_DIR_NAME = "sources"
const val SCAN_DIR_NAME = "scanned_pages"
const val THUMBNAIL_DIR_NAME = "thumbnails"

class ImageRepository(
    scanRootDir: File,
    val transformations: ImageTransformations,
    private val thumbnailSizePx: Int,
) {

    private val sourceDir: File = File(scanRootDir, SOURCE_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private val scanDir: File = File(scanRootDir, SCAN_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private val thumbnailDir: File = File(scanRootDir, THUMBNAIL_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private val metadataFile = File(scanDir, "document.json")

    private var pages: MutableList<Page> = loadPages()

    private fun loadPages(): MutableList<Page> {
        val filesOnDisk = scanDir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()

        val metadataPages = loadMetadata()?.pages

        return when {
            metadataPages != null ->
                metadataPages
                    .filter { it.file in filesOnDisk }
                    .toMutableList()
            else ->
                filesOnDisk
                    .sorted()
                    .map { Page(file = it) }
                    .toMutableList()
        }
    }

    private fun indexOfPage(id: String): Int =
        pages.indexOfFirst { it.file == id }

    private fun loadMetadata(): DocumentMetadata? =
        if (metadataFile.exists()) {
            runCatching {
                Json.decodeFromString<DocumentMetadata>(metadataFile.readText())
            }.getOrNull()
        } else null

    private fun saveMetadata() {
        val metadata = DocumentMetadata(version = 1, pages = pages)
        metadataFile.writeText(Json.encodeToString(metadata))
    }

    fun imageIds(): PersistentList<String> =
        pages.map { it.file }.toPersistentList()

    fun getPageMetadata(id: String): PageMetadata? {
        val index = indexOfPage(id)
        if (index < 0) return null
        return pages[index].toMetadata()
    }

    fun add(pageBytes: ByteArray, sourceBytes: ByteArray, metadata: PageMetadata) {
        val fileName = "${System.currentTimeMillis()}.jpg"
        val file = File(scanDir, fileName)
        file.writeBytes(pageBytes)
        writeThumbnail(file)
        File(sourceDir, fileName).writeBytes(sourceBytes)
        pages.add(
            Page(
                file = fileName,
                quad = metadata.normalizedQuad.toSerializable(),
                rotationDegrees = metadata.rotationDegrees,
                isColored = metadata.isColored
            )
        )
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
                val index = indexOfPage(id)
                if (index >= 0) {
                    val oldPage = pages[index]
                    pages[index] = oldPage.copy(file = rotatedId)
                    saveMetadata()
                }
                delete(id)
            }
        }
    }

    fun getContent(id: String): ByteArray? {
        return getFileFor(id)?.readBytes()
    }

    fun getFileFor(id: String): File? {
        val file = File(scanDir, id)
        return if (file.exists()) file else null
    }

    fun getSourceFor(id: String): ByteArray? {
        val file = File(sourceDir, id)
        return if (file.exists()) file.readBytes() else null
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
        val index = indexOfPage(id)
        if (index < 0) return

        val page = pages.removeAt(index)
        val safeIndex = newIndex.coerceIn(0, pages.size)
        pages.add(safeIndex, page)
        saveMetadata()
    }

    fun delete(id: String) {
        File(scanDir, id).delete()
        getThumbnailFile(id).delete()
        pages.removeAll { it.file == id }
        saveMetadata()
    }

    fun clear() {
        pages.clear()
        thumbnailDir.listFiles()?.forEach {
            file -> file.delete()
        }
        scanDir.listFiles()?.forEach {
            file -> file.delete()
        }
        sourceDir.listFiles()?.forEach {
            file -> file.delete()
        }
        saveMetadata() // "empty" json file
    }
}

fun Quad.toSerializable(): NormalizedQuad =
    NormalizedQuad(
        topLeft = PointD(topLeft.x, topLeft.y),
        topRight = PointD(topRight.x, topRight.y),
        bottomRight = PointD(bottomRight.x, bottomRight.y),
        bottomLeft = PointD(bottomLeft.x, bottomLeft.y)
    )

fun NormalizedQuad.toQuad(): Quad =
    Quad(
        Point(topLeft.x, topLeft.y),
        Point(topRight.x, topRight.y),
        Point(bottomRight.x, bottomRight.y),
        Point(bottomLeft.x, bottomLeft.y)
)

fun Page.toMetadata(): PageMetadata? {
    if (quad == null || isColored == null) return null
    return PageMetadata(quad.toQuad(), rotationDegrees, isColored)
}
