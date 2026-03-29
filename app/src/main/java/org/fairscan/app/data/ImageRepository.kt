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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import java.util.Collections

const val SOURCE_DIR_NAME = "sources"
const val PROCESSED_DIR_NAME = "scanned_pages"
const val THUMBNAIL_DIR_NAME = "thumbnails"

/**
 * Repository responsible for:
 * - page persistence (document.json)
 * - image files (work, source, thumbnails)
 * - page-level operations (add, rotate, move, delete)
 */
class ImageRepository(
    scanRootDir: File,
    val transformations: ImageTransformations,
    private val thumbnailSizePx: Int,
    private val scope: CoroutineScope,
) {
    private val sourceDir = File(scanRootDir, SOURCE_DIR_NAME).apply { mkdirs() }
    private val processedDir = File(scanRootDir, PROCESSED_DIR_NAME).apply { mkdirs() }
    private val thumbnailDir = File(scanRootDir, THUMBNAIL_DIR_NAME)

    private val mutex = Mutex()

    private val metadataFile = File(processedDir, "document.json")
    private val json = Json { prettyPrint = false; encodeDefaults = true }
    private var pages: PageStore = PageStore(loadPages())

    private val imageCache = createLruCache<PageViewKey, Deferred<ByteArray?>>(maxEntries = 50)
    private val thumbnailCache = createLruCache<PageViewKey, Deferred<ByteArray?>>(maxEntries = 200)

    private fun <K, V> createLruCache(maxEntries: Int): MutableMap<K, V> =
        Collections.synchronizedMap(object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<K, V>) = size > maxEntries
        })

    // --- Metadata ---

    private fun loadPages(): MutableList<PageV2> {
        thumbnailDir.deleteRecursively() // clean up dir that was used in older versions
        normalizeLegacyFiles()
        val filesOnDisk = processedDir.listFiles()
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
                    .filter { "${it.id}.jpg" in filesOnDisk }
                    .toMutableList()
            else ->
                filesOnDisk
                    .sorted()
                    .map { pageFromLegacyFileName(it) }
                    .toMutableList()
        }
    }

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

    private fun migrateFromV1(meta: DocumentMetadataV1): MutableList<PageV2> =
        meta.pages.map { pageFromLegacyFileName(it.file) }.toMutableList()

    private fun pageFromLegacyFileName(fileName: String): PageV2 {
        val name = fileName.removeSuffix(".jpg")
        val dashIndex = name.lastIndexOf('-')
        val id = if (dashIndex >= 0) name.substring(0, dashIndex) else name
        return PageV2(id)
    }

    private fun saveMetadata() {
        val metadata = DocumentMetadataV2(pages = pages.pages())
        metadataFile.writeText(json.encodeToString(metadata))
    }

    // --- Main API ---

    suspend fun pages(): List<ScanPage> = mutex.withLock {
        pages.pages().mapNotNull {
            runCatching {
                val manualRotation = Rotation.fromDegrees(it.manualRotationDegrees)
                ScanPage(it.id, manualRotation, it.toMetadata())
            }.getOrNull()
        }
    }

    suspend fun add(pageBytes: ByteArray, sourceBytes: ByteArray, metadata: PageMetadata) =
        mutex.withLock {
            val id = "${System.currentTimeMillis()}"
            val fileName = "$id.jpg"
            File(processedDir, fileName).writeBytes(pageBytes)
            File(sourceDir, fileName).writeBytes(sourceBytes)
            pages.addOrReplace(
                PageV2(
                    id = id,
                    quad = metadata.normalizedQuad.toSerializable(),
                    baseRotationDegrees = metadata.baseRotation.degrees,
                    manualRotationDegrees = Rotation.R0.degrees,
                    isColored = metadata.isColored
                )
            )
            saveMetadata()
            // Pre-populate cache for R0
            val key = PageViewKey(id, Rotation.R0)
            imageCache.put(key, CompletableDeferred(pageBytes))
        }

    suspend fun rotate(id: String, clockwise: Boolean) = mutex.withLock {
        val page = pages.get(id) ?: return@withLock
        val delta = if (clockwise) Rotation.R90 else Rotation.R270
        val newRotation = Rotation.fromDegrees(page.manualRotationDegrees).add(delta)
        pages.update(id) {
            it.copy(manualRotationDegrees = newRotation.degrees)
        }
        saveMetadata()
    }

    suspend fun jpegBytes(key: PageViewKey): ByteArray? =
        getOrCompute(imageCache, key, ::computeProcessedImage)


    suspend fun getThumbnail(key: PageViewKey): ByteArray? =
        getOrCompute(thumbnailCache, key, ::computeThumbnail)

    // --- Cache compute functions ---

    private suspend fun getOrCompute(
        cache: MutableMap<PageViewKey, Deferred<ByteArray?>>,
        key: PageViewKey,
        compute: suspend (PageViewKey) -> ByteArray?
    ): ByteArray? {
        val deferred = cache.computeIfAbsent(key) { k ->
            scope.async(Dispatchers.IO) { compute(k) }
        }
        try {
            return deferred.await()
        } catch (e: Exception) {
            cache.remove(key, deferred)
            throw e
        }
    }

    private suspend fun computeProcessedImage(key: PageViewKey): ByteArray? =
        withContext(Dispatchers.IO) {
            val baseFile = File(processedDir, "${key.pageId}.jpg")
            if (!baseFile.exists()) return@withContext null
            if (key.rotation == Rotation.R0) {
                baseFile.readBytes()
            } else {
                transformations.rotate(
                    baseFile.readBytes(),
                    key.rotation.degrees,
                    ExportQuality.BALANCED.jpegQuality)
            }
        }

    private suspend fun computeThumbnail(key: PageViewKey): ByteArray? =
        withContext(Dispatchers.IO) {
            val imageBytes = getOrCompute(imageCache, key, ::computeProcessedImage)
                ?: return@withContext null
            transformations.resize(imageBytes, thumbnailSizePx)
        }

    // --- Other operations ---

    fun sourceJpegBytes(id: String): ByteArray? {
        val file = File(sourceDir, "$id.jpg")
        return if (file.exists()) file.readBytes() else null
    }

    suspend fun movePage(id: String, newIndex: Int) = mutex.withLock {
        pages.move(id, newIndex)
        saveMetadata()
    }

    suspend fun delete(id: String) = mutex.withLock {
        pages.delete(id)
        saveMetadata()
        File(sourceDir, "$id.jpg").delete()
        processedDir.listFiles()
            ?.filter { it.name.startsWith("$id.") || it.name.startsWith("$id-") }
            ?.forEach { it.delete() }
        // No need to clean caches: stale entries will be evicted by LRU
    }

    suspend fun clear() = mutex.withLock {
        pages.clear()
        saveMetadata()
        sourceDir.listFiles()?.forEach { it.delete() }
        processedDir.listFiles()?.forEach { it.delete() }
        synchronized(imageCache) { imageCache.clear() }
        synchronized(thumbnailCache) { thumbnailCache.clear() }
    }

    // --- Legacy migration ---

    data class DiskPageFiles(
        val base: File?,
        val rotated: List<File>
    )

    // Legacy normalization strategy:
    // If only rotated files exist, keep ONE arbitrarily as base (id.jpg)
    // and discard the others. We intentionally sacrifice exact rotation
    // fidelity to restore a coherent model.
    private fun normalizeLegacyFiles() {
        val jpgs = processedDir.listFiles()?.filter { it.extension == "jpg" }.orEmpty()
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
                val target = File(processedDir, "$id.jpg")
                if (legacyFile.renameTo(target)) {
                    sortedRotatedFiles.drop(1).forEach { it.delete() }
                }
            }
        }
    }

    fun lastAddedSourceFile(): File? {
        val sourceFiles = sourceDir.listFiles()?.filter { it.extension == "jpg" }
        if (sourceFiles.isNullOrEmpty()) {
            return null
        }
        return sourceFiles.maxByOrNull { it.lastModified() }
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
    if (quad == null || isColored == null) return null
    return PageMetadata(
        quad.toQuad(),
        Rotation.fromDegrees(baseRotationDegrees),
        isColored
    )
}
