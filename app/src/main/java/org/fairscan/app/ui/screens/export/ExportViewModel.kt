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
package org.fairscan.app.ui.screens.export

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.fairscan.app.AppContainer
import org.fairscan.app.RecentDocument
import org.fairscan.app.data.GeneratedPdf
import org.fairscan.app.data.PdfFileManager
import org.fairscan.app.ui.screens.home.HomeViewModel
import java.io.File
import java.io.FileInputStream

private const val PDF_MIME_TYPE = "application/pdf"

sealed interface ExportEvent {
    data object RequestSavePdf : ExportEvent
    data object SaveError : ExportEvent
}

class ExportViewModel(container: AppContainer): ViewModel() {

    private val pdfFileManager = container.pdfFileManager
    private val imageRepository = container.imageRepository
    private val settingsRepository = container.settingsRepository
    private val recentDocumentsDataStore = container.recentDocumentsDataStore
    private val logger = container.logger

    private val _events = MutableSharedFlow<ExportEvent>()
    val events = _events.asSharedFlow()

    private suspend fun generatePdf(): GeneratedPdf = withContext(Dispatchers.IO) {
        val imageIds = imageRepository.imageIds()
        val jpegs = imageIds.asSequence()
            .map { id -> imageRepository.getContent(id) }
            .filterNotNull()
        return@withContext pdfFileManager.generatePdf(jpegs)
    }

    private val _pdfUiState = MutableStateFlow(PdfGenerationUiState())
    val pdfUiState: StateFlow<PdfGenerationUiState> = _pdfUiState.asStateFlow()

    private var generationJob: Job? = null
    private var desiredFilename: String = ""

    fun setFilename(name: String) {
        desiredFilename = name
    }

    fun startPdfGeneration() {
        cancelPdfGeneration()
        generationJob = viewModelScope.launch {
            try {
                val result = generatePdf()
                _pdfUiState.update {
                    it.copy(
                        isGenerating = false,
                        generatedPdf = result
                    )
                }
            } catch (e: Exception) {
                logger.e("FairScan", "PDF generation failed", e)
                _pdfUiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = "PDF generation failed"
                    )
                }
            }
        }
    }

    fun cancelPdfGeneration() {
        generationJob?.cancel()
        _pdfUiState.value = PdfGenerationUiState()
    }

    fun setPdfAsShared() {
        _pdfUiState.update { it.copy(hasSharedPdf = true) }
    }

    fun getFinalPdf(): GeneratedPdf? {
        val tempPdf = _pdfUiState.value.generatedPdf ?: return null
        val tempFile = tempPdf.file
        val fileName = PdfFileManager.addExtensionIfMissing(desiredFilename)
        val newFile = File(tempFile.parentFile, fileName)
        if (tempFile.absolutePath != newFile.absolutePath) {
            if (newFile.exists()) newFile.delete()
            val success = tempFile.renameTo(newFile)
            if (!success) return null
            _pdfUiState.update {
                it.copy(generatedPdf = GeneratedPdf(
                    newFile, tempPdf.sizeInBytes, tempPdf.pageCount)
                )
            }
        }
        return _pdfUiState.value.generatedPdf
    }

    fun onSavePdfClicked() {
        viewModelScope.launch {
            _events.emit(ExportEvent.RequestSavePdf)
        }
    }

    fun onRequestPdfSave(context: Context) {
        viewModelScope.launch {
            performPdfSave(context)
        }
    }

    private suspend fun performPdfSave(context: Context) {
        try {
            val pdf = getFinalPdf() ?: return

            val exportDir = settingsRepository.exportDirUri.first()
            var fileInDownloads: File? = null

            var savedName: String
            val savedUri: Uri
            if (exportDir == null) {
                fileInDownloads = pdfFileManager.copyToExternalDir(pdf.file)
                savedUri = fileInDownloads.toUri()
                savedName = fileInDownloads.name
            } else {
                val saved = copyViaSaf(context, pdf.file, exportDir.toUri())
                savedUri = saved.uri
                savedName = saved.name?:pdf.file.name
            }

            _pdfUiState.update {
                it.copy(
                    savedFileUri = savedUri,
                    exportDirName = resolveExportDirName(context, exportDir?.toUri()))
            }

            fileInDownloads?.let { mediaScan(context, it) }

            addRecentDocument(savedUri, savedName, pdf.pageCount)
        } catch (e: Exception) {
            logger.e("FairScan", "Failed to save PDF", e)
            _events.emit(ExportEvent.SaveError)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun mediaScan(context: Context, file: File) =
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(PDF_MIME_TYPE)
            ) { _, _ -> continuation.resume(Unit) {} }
        }

    private fun copyViaSaf(
        context: Context,
        source: File,
        exportDirUri: Uri,
    ): DocumentFile {
        val resolver = context.contentResolver

        val tree = DocumentFile.fromTreeUri(context, exportDirUri)
            ?: throw IllegalStateException("Invalid SAF directory")

        // Name collisions are handled automatically by SAF provider
        val target = tree.createFile(PDF_MIME_TYPE, source.name)
            ?: throw IllegalStateException("Unable to create SAF file")

        resolver.openOutputStream(target.uri)?.use { output ->
            FileInputStream(source).use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Failed to open SAF output stream")

        return target
    }

    fun cleanUpOldPdfs(thresholdInMillis: Int) {
        pdfFileManager.cleanUpOldFiles(thresholdInMillis)
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

data class PdfGenerationActions(
    val startGeneration: () -> Unit,
    val setFilename: (String) -> Unit,
    val uiStateFlow: StateFlow<PdfGenerationUiState>,// TODO is it ok to have that here?
    val sharePdf: () -> Unit,
    val savePdf: () -> Unit,
    val openPdf: () -> Unit,
)
