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
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.fairscan.app.FairScanApp
import org.fairscan.app.data.GeneratedPdf
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.PdfFileManager
import org.fairscan.app.ui.screens.home.HomeViewModel
import org.fairscan.app.ui.state.PdfGenerationUiState
import java.io.File

private const val PDF_MIME_TYPE = "application/pdf"

sealed interface ExportEvent {
    data object RequestSavePdf : ExportEvent
    data class ShowToast(val message: String) : ExportEvent
    data object PdfSaved : ExportEvent
}

class ExportViewModel(
    private val pdfFileManager: PdfFileManager,
    private val imageRepository: ImageRepository,
): ViewModel() {

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = context.applicationContext as FairScanApp
                val pdfFileManager = app.appContainer.pdfFileManager
                val imageRepository = app.appContainer.imageRepository
                return ExportViewModel(pdfFileManager, imageRepository) as T
            }
        }
    }

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
                Log.e("FairScan", "PDF generation failed", e)
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

    fun saveFile(pdfFile: File): File {
        val copiedFile = pdfFileManager.copyToExternalDir(pdfFile)
        _pdfUiState.update { it.copy(savedFileUri = copiedFile.toUri()) }
        return copiedFile
    }

    fun onSavePdfClicked() {
        viewModelScope.launch {
            _events.emit(ExportEvent.RequestSavePdf)
        }
    }

    fun onRequestPdfSave(context: Context, homeViewModel: HomeViewModel) {
        viewModelScope.launch {
            performPdfSave(context, homeViewModel)
        }
    }

    private suspend fun performPdfSave(context: Context, homeViewModel: HomeViewModel) {
        try {
            val pdf = getFinalPdf() ?: return
            val targetFile = saveFile(pdf.file)

            mediaScan(context, targetFile)

            // TODO remove that call: that should be handled through the ExportEvent
            homeViewModel.addRecentDocument(
                targetFile.absolutePath,
                pdf.pageCount
            )

            _events.emit(ExportEvent.PdfSaved)

        } catch (e: Exception) {
            Log.e("FairScan", "Failed to save PDF", e)
            _events.emit(ExportEvent.ShowToast("Error while saving PDF"))
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

    fun cleanUpOldPdfs(thresholdInMillis: Int) {
        pdfFileManager.cleanUpOldFiles(thresholdInMillis)
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
