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
package org.fairscan.app.data

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.content.ContentValues
import android.content.Context
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fairscan.app.ui.screens.export.ExportResult
import org.fairscan.app.ui.screens.export.SavedBundle
import org.fairscan.app.ui.screens.export.SavedItem
import org.fairscan.app.ui.screens.settings.ExportFormat
import org.fairscan.app.ui.uriForFile
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class LibraryDocument(
    val uri: Uri,
    val name: String,
    val format: ExportFormat,
    val size: Long,
    val modifiedAt: Long,
)

/** The public Documents/FairScan folder, independent of disposable scan/preparation files. */
class DocumentLibrary(private val context: Context) {
    private val resolver = context.contentResolver
    private val mutex = Mutex()
    private val legacyDirectory get() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FairScan"
    )

    suspend fun documents(): List<LibraryDocument> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Downloads collection only permits paths under Download/ on current Android.
            // FairScan is a non-media document library in Documents/FairScan, so use Files.
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.MediaColumns.IS_PENDING} = 0"
            resolver.query(collection, projection, selection, arrayOf(RELATIVE_PATH),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC, ${MediaStore.MediaColumns._ID} DESC"
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val format = ExportFormat.entries.find { it.mimeType == cursor.getString(2) }
                            ?: continue
                        add(LibraryDocument(
                            ContentUris.withAppendedId(collection, cursor.getLong(0)),
                            cursor.getString(1), format, cursor.getLong(3), cursor.getLong(4) * 1000,
                        ))
                    }
                }
            } ?: throw IOException("Cannot read the document library")
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Storage permission is required on Android 8 and 9")
            }
            if (!legacyDirectory.exists()) return@withContext emptyList()
            val files = legacyDirectory.listFiles() ?: throw IOException("Cannot read FairScan folder")
            files.filter { it.isFile && it.extension.lowercase() in setOf("pdf", "jpg", "jpeg") }
                .sortedByDescending { it.lastModified() }
                .map { file -> LibraryDocument(
                    uriForFile(context, file), file.name,
                    if (file.extension.equals("pdf", true)) ExportFormat.PDF else ExportFormat.JPEG,
                    file.length(), file.lastModified(),
                ) }
        }
    }

    suspend fun save(result: ExportResult): SavedBundle = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(result.files.isNotEmpty())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveModern(result)
            else saveLegacy(result)
        }
    }

    suspend fun importDocuments(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            var imported = 0
            for (sourceUri in uris) {
                val format = importFormat(sourceUri) ?: continue
                val name = importedName(sourceUri, format)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    importModern(sourceUri, name, format)
                } else {
                    importLegacy(sourceUri, name)
                }
                imported++
            }
            imported
        }
    }

    suspend fun deleteDocuments(documents: List<LibraryDocument>): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            var deleted = 0
            for (document in documents) {
                val succeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.delete(document.uri, null, null) == 1
                } else {
                    File(legacyDirectory, document.name).delete()
                }
                if (!succeeded) throw IOException("Cannot delete ${document.name}")
                deleted++
            }
            deleted
        }
    }

    suspend fun renameDocument(document: LibraryDocument, requestedName: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val extension = when (document.format) {
                ExportFormat.PDF -> ".pdf"
                ExportFormat.JPEG -> if (document.name.endsWith(".jpeg", true)) ".jpeg" else ".jpg"
            }
            val base = requestedName.trim().substringBeforeLast('.', requestedName.trim())
                .trim().ifBlank { document.name.substringBeforeLast('.') }
            val targetName = safeDocumentName(base + extension)
            if (targetName == document.name) return@withLock
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uniqueName = uniqueModernName(targetName)
                val updated = resolver.update(document.uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
                }, null, null)
                if (updated != 1) throw IOException("Cannot rename ${document.name}")
            } else {
                val source = File(legacyDirectory, document.name)
                val target = availableDocumentFile(legacyDirectory, targetName)
                if (!source.renameTo(target)) throw IOException("Cannot rename ${document.name}")
            }
        }
    }

    private fun importFormat(uri: Uri): ExportFormat? {
        val name = sourceName(uri)
        return when {
            resolver.getType(uri)?.equals("application/pdf", ignoreCase = true) == true ||
                name.endsWith(".pdf", ignoreCase = true) -> ExportFormat.PDF
            resolver.getType(uri)?.equals("image/jpeg", ignoreCase = true) == true ||
                name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) ->
                ExportFormat.JPEG
            else -> null
        }
    }

    private fun importedName(uri: Uri, format: ExportFormat): String {
        val originalName = safeDocumentName(sourceName(uri))
        return if (originalName.substringAfterLast('.', "").isNotEmpty()) originalName
        else originalName + if (format == ExportFormat.PDF) ".pdf" else ".jpg"
    }

    private fun sourceName(uri: Uri): String = resolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf(String::isNotBlank) ?: uri.lastPathSegment ?: "FairScan"

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun importModern(sourceUri: Uri, name: String, format: ExportFormat) {
        val targetName = uniqueModernName(name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val targetUri = resolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        ) ?: throw IOException("Cannot create imported document")
        try {
            val input = resolver.openInputStream(sourceUri) ?: throw IOException("Cannot read selected document")
            val output = resolver.openOutputStream(targetUri) ?: throw IOException("Cannot write imported document")
            input.use { source -> output.use { source.copyTo(it) } }
            if (resolver.update(targetUri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) != 1) throw IOException("Cannot finish importing document")
        } catch (error: Exception) {
            runCatching { resolver.delete(targetUri, null, null) }
            throw error
        }
    }

    private fun importLegacy(sourceUri: Uri, name: String) {
        if (!legacyDirectory.isDirectory && !legacyDirectory.mkdirs()) {
            throw IOException("Cannot create FairScan folder")
        }
        val target = availableDocumentFile(legacyDirectory, name)
        try {
            val input = resolver.openInputStream(sourceUri) ?: throw IOException("Cannot read selected document")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun uniqueModernName(name: String): String {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(RELATIVE_PATH),
            null,
        )?.use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0).lowercase()) }
        } ?: emptySet()
        val safeName = safeDocumentName(name)
        if (safeName.lowercase() !in existing) return safeName
        val base = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "").takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()
        var suffix = 1
        while ("$base ($suffix)$extension".lowercase() in existing) suffix++
        return "$base ($suffix)$extension"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveModern(result: ExportResult): SavedBundle {
        val created = mutableListOf<Uri>()
        try {
            val items = result.files.map { source ->
                coroutineContext.ensureActive()
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, result.format.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
                )
                    ?: throw IOException("Cannot create document")
                created += uri
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { it.copyTo(output) }
                } ?: throw IOException("Cannot write document")
                val actualName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                    ?: source.name
                SavedItem(uri, actualName, result.format)
            }
            coroutineContext.ensureActive()
            for (uri in created) {
                if (resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) != 1) throw IOException("Cannot finish saving document")
            }
            return SavedBundle(items)
        } catch (error: Exception) {
            // Do not leave corrupt PDFs, incomplete JPEG batches or abandoned pending entries.
            created.forEach { runCatching { resolver.delete(it, null, null) } }
            throw error
        }
    }

    private suspend fun saveLegacy(result: ExportResult): SavedBundle {
        if (!legacyDirectory.isDirectory && !legacyDirectory.mkdirs()) {
            throw IOException("Cannot create FairScan folder")
        }
        val created = mutableListOf<File>()
        try {
            val items = result.files.map { source ->
                coroutineContext.ensureActive()
                val target = availableDocumentFile(legacyDirectory, source.name)
                created += target
                source.copyTo(target)
                SavedItem(uriForFile(context, target), target.name, result.format)
            }
            return SavedBundle(items)
        } catch (error: Exception) {
            created.forEach { it.delete() }
            throw error
        }
    }

    companion object {
        const val RELATIVE_PATH = "Documents/FairScan/"
    }
}
