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
package org.fairscan.app.ui.screens.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.AppContainer
import org.fairscan.app.RecentDocument
import org.fairscan.app.data.FileManager
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.jpegsForExport
import org.fairscan.app.ui.screens.settings.ExportFormat
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed interface ExportEvent {
    data object RequestSave : ExportEvent
    data object SaveError : ExportEvent
}

class ExportViewModel(container: AppContainer, val imageRepository: ImageRepository): ViewModel() {

    private val preparationDir = container.preparationDir
    private val fileManager = container.fileManager
    private val settingsRepository = container.settingsRepository
    private val recentDocumentsDataStore = container.recentDocumentsDataStore
    private val logger = container.logger

    private val _events = MutableSharedFlow<ExportEvent>()
    val events = _events.asSharedFlow()

    private suspend fun generatePdf(
        exportQuality: ExportQuality
    ): ExportResult.Pdf = withContext(Dispatchers.IO) {
        val jpegs = jpegsForExport(imageRepository, exportQuality)
        val pdf = fileManager.generatePdf(jpegs)
        return@withContext ExportResult.Pdf(pdf.file, pdf.sizeInBytes, pdf.pageCount)
    }

    suspend fun generatePdfForExternalCall(): ExportResult.Pdf {
        return generatePdf(ExportQuality.BALANCED)
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var preparationJob: Job? = null
    private var desiredFilename: String = ""
    private var exportFormat = ExportFormat.PDF

    fun setFilename(name: String) {
        desiredFilename = name
    }

    fun initializeExportScreen() {
        cancelPreparation()

        preparationJob = viewModelScope.launch {
            val exportQuality = settingsRepository.exportQuality.first()
            exportFormat = settingsRepository.exportFormat.first()
            _uiState.update { it.copy(format = exportFormat, isGenerating = true) }
            try {
                val t1 = System.currentTimeMillis()
                val result = if (exportFormat == ExportFormat.JPEG) {
                    generateJpegs(exportQuality)
                } else {
                    generatePdf(exportQuality)
                }
                _uiState.update {
                    it.copy(isGenerating = false, result = result)
                }
                val t2 = System.currentTimeMillis()
                val pageCount = result.pageCount
                Log.i("Export", "Generation: $pageCount pages, $exportQuality, ${t2-t1} ms")
            } catch (e: Exception) {
                val message = "Failed to prepare $exportFormat export"
                logger.e("FairScan", message, e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = message
                    )
                }
            }
        }
    }

    private suspend fun generateJpegs(
        exportQuality: ExportQuality
    ): ExportResult.Jpeg = withContext(Dispatchers.IO) {
        val jpegs = jpegsForExport(imageRepository, exportQuality)
        val timestamp = System.currentTimeMillis()
        preparationDir.mkdirs()
        val files = jpegs.mapIndexed { index, bytes ->
            val file = File(preparationDir, "$timestamp-${index + 1}.jpg")
            file.writeBytes(bytes)
            file
        }.toList()
        val sizeInBytes = files.sumOf { it.length() }
        ExportResult.Jpeg(files, sizeInBytes)
    }

    fun cancelPreparation() {
        preparationJob?.cancel()
        _uiState.value = ExportUiState()
    }

    fun setAsShared() {
        _uiState.update { it.copy(hasShared = true) }
    }

    fun applyRenaming(): ExportResult? {
        val result = _uiState.value.result ?: return null
        when (result) {
            is ExportResult.Pdf -> {
                val fileName = FileManager.addPdfExtensionIfMissing(desiredFilename)
                val newFile = File(result.file.parentFile, fileName)
                val tempFile = result.file
                if (tempFile.absolutePath != newFile.absolutePath) {
                    if (newFile.exists()) newFile.delete()
                    val success = tempFile.renameTo(newFile)
                    if (!success) return null
                    _uiState.update {
                        it.copy(result = ExportResult.Pdf(
                            newFile, result.sizeInBytes, result.pageCount)
                        )
                    }
                }
            }
            is ExportResult.Jpeg -> {
                val base = desiredFilename.removeSuffix(".jpg")
                val files = result.files
                val renamedFiles = files.mapIndexed { index, file ->
                    val indexSuffix = if (files.size == 1) "" else "_${index + 1}"
                    val newFile = File(file.parentFile, "${base}${indexSuffix}.jpg")
                    if (file.absolutePath != newFile.absolutePath) {
                        if (newFile.exists()) newFile.delete()
                        file.renameTo(newFile)
                    }
                    newFile
                }
                val updated = result.copy(jpegFiles = renamedFiles)
                _uiState.update { it.copy(result = updated) }
            }
        }
        return _uiState.value.result
    }

    fun onSaveClicked() {
        viewModelScope.launch {
            _events.emit(ExportEvent.RequestSave)
        }
    }

    fun onRequestSave(context: Context) {
        viewModelScope.launch {
            try {
                save(context)
            } catch (e: Exception) {
                logger.e("FairScan", "Failed to save PDF", e)
                _events.emit(ExportEvent.SaveError)
            }
        }
    }

    private suspend fun save(context:Context) {
        val result = applyRenaming() ?: return
        val exportDir = settingsRepository.exportDirUri.first()?.toUri()
        val savedItems = mutableListOf<SavedItem>()
        val filesForMediaScan = mutableListOf<File>()

        for (file in result.files) {
            val saved = if (exportDir == null) {
                // No export dir defined -> save to Downloads
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+: use MediaStore API
                    val uri = saveViaMediaStore(context, file, exportFormat)
                    SavedItem(uri, file.name, exportFormat)
                } else {
                    // Android 8 and 9: use File API
                    // (MediaStore doesn't allow to choose Downloads for Android<10)
                    val out = fileManager.copyToExternalDir(file)
                    filesForMediaScan.add(out)
                    SavedItem(out.toUri(), out.name, exportFormat)
                }
            } else {
                // Use Storage Access Framework to save to the chosen directory
                val safFile = saveViaSaf(context, file, exportDir, exportFormat)
                SavedItem(safFile.uri, safFile.name ?: file.name, exportFormat)
            }
            savedItems += saved
        }

        val exportDirName = resolveExportDirName(context, exportDir)
        val bundle = SavedBundle(savedItems, exportDir, exportDirName)
        _uiState.update { it.copy(savedBundle = bundle) }

        if (exportFormat == ExportFormat.PDF) {
            savedItems.forEach { item ->
                addRecentDocument(item.uri, item.fileName, result.pageCount)
            }
        }

        filesForMediaScan.forEach { f -> mediaScan(context, f, exportFormat.mimeType) }
    }

    private suspend fun mediaScan(
        context: Context,
        file: File,
        mimeType: String
    ): Uri? = suspendCoroutine { cont ->
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType)
        ) { _, uri ->
            cont.resume(uri)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(
        context: Context,
        source: File,
        format: ExportFormat
    ): Uri {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input ->
                input.copyTo(out)
            }
        } ?: throw IOException("Failed to open output stream")

        return uri
    }

    private fun saveViaSaf(
        context: Context,
        source: File,
        exportDirUri: Uri,
        exportFormat: ExportFormat,
    ): DocumentFile {
        val resolver = context.contentResolver

        val tree = DocumentFile.fromTreeUri(context, exportDirUri)
            ?: throw IllegalStateException("Invalid SAF directory")

        // Name collisions are handled automatically by SAF provider
        val target = tree.createFile(exportFormat.mimeType, source.name)
            ?: throw IllegalStateException("Unable to create SAF file")

        resolver.openOutputStream(target.uri)?.use { output ->
            FileInputStream(source).use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Failed to open SAF output stream")

        return target
    }

    fun cleanUpOldPreparedFiles(thresholdInMillis: Int) {
        fileManager.cleanUpOldFiles(thresholdInMillis)
    }

    private fun resolveExportDirName(context: Context, exportDirUri: Uri?): String? {
        return if (exportDirUri == null) {
            null
        } else {
            DocumentFile.fromTreeUri(context, exportDirUri)?.name
        }
    }

    fun addRecentDocument(fileUri: Uri, fileName: String, pageCount: Int) {
        viewModelScope.launch {
            recentDocumentsDataStore.updateData { current ->
                val newDoc = RecentDocument.newBuilder()
                    .setFileUri(fileUri.toString())
                    .setFileName(fileName)
                    .setPageCount(pageCount)
                    .setCreatedAt(System.currentTimeMillis())
                    .build()
                current.toBuilder()
                    .addDocuments(0, newDoc)
                    .also { builder ->
                        while (builder.documentsCount > 3) {
                            builder.removeDocuments(builder.documentsCount - 1)
                        }
                    }
                    .build()
            }
        }
    }
}

sealed class ExportResult {
    abstract val files: List<File>
    abstract val sizeInBytes: Long
    abstract val pageCount: Int
    abstract val format: ExportFormat

    data class Pdf(
        val file: File,
        override val sizeInBytes: Long,
        override val pageCount: Int,
    ) : ExportResult() {
        override val files get() = listOf(file)
        override val format: ExportFormat = ExportFormat.PDF
    }

    data class Jpeg(
        val jpegFiles: List<File>,
        override val sizeInBytes: Long,
    ) : ExportResult() {
        override val files get() = jpegFiles
        override val pageCount get() = jpegFiles.size
        override val format: ExportFormat = ExportFormat.JPEG
    }
}

data class ExportActions(
    val initializeExportScreen: () -> Unit,
    val setFilename: (String) -> Unit,
    val share: () -> Unit,
    val save: () -> Unit,
    val open: (SavedItem) -> Unit,
)
