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
package org.fairscan.app.ui.screens.export

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
import org.fairscan.app.R
import org.fairscan.app.data.safeDocumentName
import org.fairscan.app.data.FileManager
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.jpegsToExport
import org.fairscan.app.domain.pagesToExport
import org.fairscan.app.ui.screens.settings.ExportFormat
import org.fairscan.app.ui.screens.settings.ExportFormat.PDF
import org.fairscan.imageprocessing.ColorMode
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.CancellationException

sealed interface ExportEvent {
    data class RequestSave(
        val shareAfter: Boolean = false,
        val closeAfter: Boolean = false,
        val copyToCustomFolder: Boolean = false,
    ) : ExportEvent
    data class Share(val result: SavedBundle) : ExportEvent
    data object Finished : ExportEvent
}

class ExportViewModel(container: AppContainer, val imageRepository: ImageRepository): ViewModel() {

    private val preparationDir = container.preparationDir
    private val fileManager = container.fileManager
    private val documentLibrary = container.documentLibrary
    private var archivedKey: Pair<ExportPreparationKey?, String>? = null
    private var archivedBundle: SavedBundle? = null
    var pendingRequest = ExportEvent.RequestSave()
        private set
    private val settingsRepository = container.settingsRepository
    private val ocrService = container.ocrService
    private val logger = container.logger

    private val _events = MutableSharedFlow<ExportEvent>()
    val events = _events.asSharedFlow()

    private suspend fun generatePdf(
        exportQuality: ExportQuality,
        disableOcr: Boolean,
        onPageCompleted: (Int) -> Unit,
    ): ExportResult.Pdf = withContext(Dispatchers.IO) {
        val pageToExports = pagesToExport(imageRepository, exportQuality)
        val pdf = fileManager.generatePdf(pageToExports, disableOcr, onPageCompleted)
        return@withContext ExportResult.Pdf(pdf.file, pdf.sizeInBytes, pdf.pageCount)
    }

    suspend fun generatePdfForExternalCall(): ExportResult.Pdf {
        val pdf = generatePdf(ExportQuality.BALANCED, true) {}
        val sourceFile = pdf.file
        val targetFile = File(sourceFile.parentFile, defaultFilename() + ".pdf")
        if (sourceFile.absolutePath == targetFile.absolutePath) return pdf
        if (targetFile.exists() || !sourceFile.renameTo(targetFile)) return pdf
        return pdf.copy(file = targetFile)
    }

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var lastPreparationKey: ExportPreparationKey? = null
    private var preparationJob: Job? = null

    fun setFilename(name: String) {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(filename = name, isUserDefinedFilename = true, savedBundle = null, hasShared = false)
        }
    }

    fun resetFilename() {
        _uiState.value = ExportUiState()
        lastPreparationKey = null
        archivedKey = null
        archivedBundle = null
    }

    private fun defaultFilename(): String {
        return settingsRepository.defaultFilenameStyle.value.filename()
    }

    private fun ensureValidFilename() {
        _uiState.update {
            val normalized = if (it.filename.isBlank()) "" else safeDocumentName(it.filename)
            if (normalized.isEmpty() || !it.isUserDefinedFilename) {
                it.copy(filename = defaultFilename(), isUserDefinedFilename = false)
            } else if (normalized != it.filename) {
                it.copy(filename = normalized)
            } else it
        }
    }

    private suspend fun currentPageKeys(): ImmutableList<PageViewKey> =
        imageRepository.pages().map { it.key() }.toImmutableList()

    fun cancelPreparationJob() {
        preparationJob?.let {
            // keep result if job is not active
            if (it.isActive) {
                _uiState.update { ExportUiState() }
            }
            it.cancel()
        }
    }

    fun prepareExportIfNeeded() {
        ensureValidFilename()

        viewModelScope.launch {
            val exportQuality = settingsRepository.exportQuality.first()
            val exportFormat = settingsRepository.exportFormat.first()
            val ocrLanguageString = ocrService.languageString()

            val currentPageKeys = currentPageKeys()
            val key = ExportPreparationKey(
                currentPageKeys, exportFormat, exportQuality, ocrLanguageString)
            if (key == lastPreparationKey && _uiState.value.result != null) {
                return@launch
            }
            val pageCount = currentPageKeys.size

            lastPreparationKey = key
            preparationJob?.cancel()
            preparationJob?.join()

            preparationJob = launch {
                val ocrActivation = if (exportFormat == PDF) ocrLanguageString.isNotEmpty() else null
                val blackAndWhiteAsJpeg = exportFormat == ExportFormat.JPEG &&
                    currentPageKeys.any { it.colorMode == ColorMode.BLACK_AND_WHITE }
                _uiState.update {
                    ExportUiState(
                        filename = it.filename,
                        format = exportFormat,
                        isGenerating = true,
                        progress = ExportProgress(0, pageCount),
                        ocrActivation = ocrActivation,
                        blackAndWhiteAsJpeg = blackAndWhiteAsJpeg,
                    )
                }
                val onPageCompleted: (Int) -> Unit = { completedPages ->
                    ensureActive()
                    _uiState.update {
                        it.copy(progress = ExportProgress(completedPages, pageCount))
                    }
                }
                try {
                    val t1 = System.currentTimeMillis()
                    val result = if (exportFormat == ExportFormat.JPEG) {
                        generateJpegs(exportQuality, onPageCompleted)
                    } else {
                        generatePdf(exportQuality, false, onPageCompleted)
                    }
                    _uiState.update { it.copy(result = result) }
                    val t2 = System.currentTimeMillis()
                    Log.i("Export", "Generation: $pageCount pages, $exportQuality, ${t2 - t1} ms")
                } catch (e: CancellationException) {
                    // Preparation cancelled: do nothing
                    throw e
                } catch (e: Exception) {
                    val message = "Failed to prepare $exportFormat export"
                    logger.e("FairScan", message, e)
                    _uiState.update {
                        it.copy(error = ExportError.OnPrepareOrShare(message, e))
                    }
                } finally {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    private suspend fun generateJpegs(
        exportQuality: ExportQuality,
        onPageCompleted: (Int) -> Unit,
    ): ExportResult.Jpeg = withContext(Dispatchers.IO) {
        val jpegPages = jpegsToExport(imageRepository, exportQuality)
        val timestamp = System.currentTimeMillis()
        preparationDir.mkdirs()
        val files = jpegPages.mapIndexed { index, page ->
            val file = File(preparationDir, "$timestamp-${index + 1}.jpg")
            file.writeBytes(page.image.get().bytes)
            onPageCompleted(index + 1)
            file
        }.toList()
        val sizeInBytes = files.sumOf { it.length() }
        ExportResult.Jpeg(files, sizeInBytes)
    }

    private fun renameFile(source: File, target: File) {
        if (source.absolutePath == target.absolutePath) return
        if (target.exists() && !target.delete()) {
            throw IOException("Cannot delete existing file ${target.absolutePath}")
        }
        if (!source.renameTo(target)) {
            throw IOException("Failed to rename ${source.name} to ${target.name}")
        }
    }

    private fun applyRenaming(): ExportResult {
        val result = _uiState.value.result
            ?: throw IllegalStateException("Export result missing")
        ensureValidFilename()
        val filename = _uiState.value.filename
        val updated = when (result) {
            is ExportResult.Pdf -> {
                val fileName = FileManager.addPdfExtensionIfMissing(filename)
                val newFile = File(result.file.parentFile, fileName)
                renameFile(result.file, newFile)
                ExportResult.Pdf(newFile, result.sizeInBytes, result.pageCount)
            }
            is ExportResult.Jpeg -> {
                val base = filename.removeSuffix(".jpg")
                val files = result.files
                val renamedFiles = files.mapIndexed { index, file ->
                    val indexSuffix = if (files.size == 1) "" else "_${index + 1}"
                    val newFile = File(file.parentFile, "${base}${indexSuffix}.jpg")
                    renameFile(file, newFile)
                    newFile
                }
                result.copy(jpegFiles = renamedFiles)
            }
        }
        _uiState.update { it.copy(result = updated) }
        return updated
    }

    private fun requestSave(request: ExportEvent.RequestSave) {
        if (_uiState.value.isSaving || _uiState.value.isGenerating || _uiState.value.result == null) return
        pendingRequest = request
        viewModelScope.launch { _events.emit(request) }
    }

    fun onShareClicked() = requestSave(ExportEvent.RequestSave(shareAfter = true))

    fun onSaveClicked() = requestSave(ExportEvent.RequestSave(copyToCustomFolder = true))

    fun onFinishClicked() = requestSave(ExportEvent.RequestSave(closeAfter = true))

    fun onRequestSave(context: Context, request: ExportEvent.RequestSave) {
        if (_uiState.value.isSaving || _uiState.value.result == null) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            var destination: SaveDir? = null
            try {
                val libraryBundle = withContext(Dispatchers.IO) {
                    val result = applyRenaming()
                    val key = lastPreparationKey to _uiState.value.filename
                    val bundle = if (key == archivedKey && archivedBundle != null) {
                        archivedBundle!!
                    } else {
                        documentLibrary.save(result).also {
                            archivedKey = key
                            archivedBundle = it
                        }
                    }
                    // The library copy is always retained, including when exporting elsewhere.
                    _uiState.update { it.copy(savedBundle = bundle) }
                    if (request.copyToCustomFolder) {
                        destination = saveDir(context)
                        destination?.let { dir ->
                            if (!context.contentResolver.persistedUriPermissions.any {
                                it.uri == dir.uri && it.isWritePermission
                            }) throw MissingExportDirPermissionException(dir.uri)
                            val items = result.files.map { file ->
                                val target = saveViaSaf(context, file, dir.uri, result.format)
                                SavedItem(target.uri, target.name ?: file.name, result.format)
                            }
                            _uiState.update { it.copy(savedBundle = SavedBundle(items, dir)) }
                        }
                    }
                    bundle
                }
                if (request.shareAfter) {
                    _events.emit(ExportEvent.Share(libraryBundle))
                    _uiState.update { it.copy(hasShared = true) }
                }
                if (request.closeAfter) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(ExportEvent.Finished)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingExportDirPermissionException) {
                logger.e("FairScan", "Missing export dir permission", e)
                _uiState.update { it.copy(error = ExportError.OnSave(
                    R.string.error_export_dir_permission_lost, destination, e)) }
            } catch (e: Exception) {
                logger.e("FairScan", "Failed to save document", e)
                _uiState.update { it.copy(error = ExportError.OnSave(R.string.error_save, destination, e)) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun saveDir(context: Context): SaveDir? {
        val uri = settingsRepository.exportDirUri.first()?.toUri() ?: return null
        return SaveDir(uri, resolveExportDirName(context, uri))
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
}

data class ExportPreparationKey(
    val pages: ImmutableList<PageViewKey>,
    val format: ExportFormat,
    val quality: ExportQuality,
    val ocrLanguageString: String,
)

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
        override val format: ExportFormat = PDF
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
    val prepareExportIfNeeded: () -> Unit,
    val setFilename: (String) -> Unit,
    val share: () -> Unit,
    val save: () -> Unit,
    val open: (SavedItem) -> Unit,
    val cancelPreparationJob: () -> Unit,
)

class MissingExportDirPermissionException(
    val uri: Uri
) : IllegalStateException(
    "Missing persisted write permission for export dir: $uri"
)
