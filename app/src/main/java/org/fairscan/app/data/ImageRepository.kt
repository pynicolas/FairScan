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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation
import org.fairscan.app.domain.ScanPage
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

    private val pagesById = mutableMapOf<String, PageV2>()
    private var pages: MutableList<PageV2> = loadPages().also {
        pagesById.putAll(it.associateBy { p -> p.id })
    }

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    private fun loadPages(): MutableList<PageV2> {
        normalizeLegacyFiles()
        val filesOnDisk = scanDir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()

        val metadataPages = if (metadataFile.exists()) {
            runCatching { loadMetadata() }.getOrNull()
        } else null

        return when {
            metadataPages != null ->
                metadataPages
                    .filter { it.workFileName() in filesOnDisk }
                    .toMutableList()
            else ->
                filesOnDisk
                    .sorted()
                    .map { pageFromFileName(it) }
                    .toMutableList()
        }
    }

    private fun indexOfPage(id: String): Int =
        pages.indexOfFirst { it.id == id }

    private fun loadMetadata(): List<PageV2> {
        val json = metadataFile.readText()

        val jsonElement = Json.parseToJsonElement(json)
        val version = jsonElement.jsonObject["version"]?.jsonPrimitive?.int ?: 1

        return when (version) {
            1 -> migrateFromV1(Json.decodeFromJsonElement<DocumentMetadataV1>(jsonElement))
            2 -> Json.decodeFromJsonElement<DocumentMetadataV2>(jsonElement).pages
            else -> error("Unsupported metadata version: $version")
        }
    }

    private fun migrateFromV1(meta: DocumentMetadataV1): MutableList<PageV2> {
        return meta.pages.map {
            old -> pageFromFileName(old.file)
        }.toMutableList()
    }

    private fun pageFromFileName(fileName: String): PageV2 {
        val fileName = fileName.removeSuffix(".jpg")
        val dashIndex = fileName.lastIndexOf('-')
        val rotation = if (dashIndex >= 0)
            fileName.substring(dashIndex + 1).toInt()
        else
            0
        val id = if (dashIndex >= 0) fileName.substring(0, dashIndex) else fileName
        return PageV2(id, manualRotationDegrees = rotation)
    }

    private fun saveMetadata() {
        val metadata = DocumentMetadataV2(pages = pages)
        metadataFile.writeText(json.encodeToString(metadata))
    }

    fun pages(): List<ScanPage> =
        pages.map {
            ScanPage(it.id, it.toMetadata())
        }

    private fun page(id: String): PageV2? = pagesById[id]

    fun add(pageBytes: ByteArray, sourceBytes: ByteArray, metadata: PageMetadata) {
        val id = "${System.currentTimeMillis()}"
        val fileName = "$id.jpg"
        val file = File(scanDir, fileName)
        file.writeBytes(pageBytes)
        writeThumbnail(file)
        File(sourceDir, fileName).writeBytes(sourceBytes)
        val page = PageV2(
            id = id,
            quad = metadata.normalizedQuad.toSerializable(),
            baseRotationDegrees = metadata.baseRotation.degrees,
            manualRotationDegrees = metadata.manualRotation.degrees,
            isColored = metadata.isColored
        )
        pagesById[page.id] = page
        pages.add(page)
        saveMetadata()
    }

    private fun workFileName(key: PageViewKey): String =
        workFileName(key.pageId, key.rotation)

    private fun workFileName(pageId: String, manualRotation: Rotation): String =
        workFileName(pageId, manualRotation.degrees)

    private fun workFileName(pageId: String, manualRotationDegrees: Int): String =
        when (manualRotationDegrees) {
            0 -> "$pageId.jpg"
            else -> "$pageId-${manualRotationDegrees}.jpg"
        }

    fun PageV2.workFileName() = workFileName(id, manualRotationDegrees)

    fun rotate(id: String, clockwise: Boolean) {
        val index = indexOfPage(id)
        if (index < 0)
            return
        val page = pages[index]

        val delta = if (clockwise) Rotation.R90 else Rotation.R270
        val currentManualRotation = Rotation.fromDegrees(page.manualRotationDegrees)
        val newManualRotation = currentManualRotation.add(delta)
        if (newManualRotation == currentManualRotation) {
            return // no-op
        }

        val inputFile = File(scanDir, "$id.jpg")
        if (!inputFile.exists()) {
            return
        }
        val outputFile = File(scanDir, workFileName(id, newManualRotation.degrees))
        if (!outputFile.exists()) {
            val jpegQuality = ExportQuality.BALANCED.jpegQuality
            transformations.rotate(inputFile, outputFile, newManualRotation.degrees, jpegQuality)
        }

        val updated = page.copy(manualRotationDegrees = newManualRotation.degrees)
        pagesById[id] = updated
        pages[index] = updated
        saveMetadata()
    }

    fun jpegBytes(key: PageViewKey): ByteArray? {
        val file = File(scanDir, workFileName(key))
        return (if (file.exists()) file else null)?.readBytes()
    }

    fun jpegBytes(id: String): ByteArray? {
        val page = page(id)
        if (page == null) return null
        val file =  File(scanDir, page.workFileName())
        return (if (file.exists()) file else null)?.readBytes()
    }

    fun sourceJpegBytes(id: String): ByteArray? {
        val file = getSourceFile(id)
        return if (file.exists()) file.readBytes() else null
    }

    private fun getSourceFile(id: String): File {
        return File(sourceDir, "$id.jpg")
    }

    fun getThumbnail(key: PageViewKey): ByteArray? {
        val thumbFile = getThumbnailFile(key)
        if (thumbFile == null) {
            return null
        }
        if (!thumbFile.exists()) {
            val workFile = File(scanDir, workFileName(key))
            if (!workFile.exists()) return null
            writeThumbnail(workFile)
        }
        return if (thumbFile.exists()) thumbFile.readBytes() else null
    }

    private fun writeThumbnail(originalFile: File) {
        val thumbFile = File(thumbnailDir, originalFile.name)
        transformations.resize(originalFile, thumbFile, thumbnailSizePx)
    }

    private fun getThumbnailFile(key: PageViewKey): File? {
        return File(thumbnailDir, workFileName(key))
    }

    fun movePage(id: String, newIndex: Int) {
        val index = indexOfPage(id)
        if (index < 0) return

        val page = pages.removeAt(index)
        val safeIndex = newIndex.coerceIn(0, pages.size)
        pages.add(safeIndex, page)
        saveMetadata()
    }

    fun delete(id: String) {
        val index = indexOfPage(id)
        if (index < 0)
            return
        pages.removeAt(index)
        saveMetadata()
        pagesById.remove(id)

        getSourceFile(id).delete()
        scanDir.listFiles()
            ?.filter { it.name.startsWith("${id}.") || it.name.startsWith("$id-") }
            ?.forEach { it.delete() }
        thumbnailDir.listFiles()
            ?.filter { it.name.startsWith("${id}.") || it.name.startsWith("$id-") }
            ?.forEach { it.delete() }
    }

    fun clear() {
        pages.clear()
        saveMetadata() // "empty" json file
        pagesById.clear()

        thumbnailDir.listFiles()?.forEach {
            file -> file.delete()
        }
        scanDir.listFiles()?.forEach {
            file -> file.delete()
        }
        sourceDir.listFiles()?.forEach {
            file -> file.delete()
        }
    }

    data class DiskPageFiles(
        val base: File?,
        val rotated: List<File>
    )

    // Legacy normalization strategy:
    // If only rotated files exist, keep ONE arbitrarily as base (id.jpg)
    // and discard the others. We intentionally sacrifice exact rotation
    // fidelity to restore a coherent model.
    private fun normalizeLegacyFiles() {
        val jpgs = scanDir.listFiles()?.filter { it.extension == "jpg" }.orEmpty()
        val byId = jpgs.groupBy { file ->
            val name = file.name.removeSuffix(".jpg")
            val dash = name.lastIndexOf('-')
            if (dash >= 0) name.substring(0, dash) else name
        }
        val pages = byId.mapValues { (_, files) ->
            val base = files.find { !it.name.contains('-') }
            val rotated = files.filter { it.name.contains('-') }
            DiskPageFiles(base, rotated)
        }
        pages.forEach { (id, files) ->
            if (files.base == null && files.rotated.isNotEmpty()) {
                val sortedRotatedFiles = files.rotated.sortedBy { it.name }
                val legacyFile = sortedRotatedFiles.first()
                val target = File(scanDir, "$id.jpg")
                if (legacyFile.renameTo(target)) {
                    sortedRotatedFiles.drop(1).forEach { it.delete() }
                }
            }
        }
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

fun PageV2.toMetadata(): PageMetadata? {
    return runCatching {
        if (quad == null || isColored == null) return null
        PageMetadata(
            quad.toQuad(),
            Rotation.fromDegrees(baseRotationDegrees),
            Rotation.fromDegrees(manualRotationDegrees),
            isColored
        )
    }.getOrNull()
}
