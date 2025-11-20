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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.data.GeneratedPdf
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.PdfFileManager
import org.fairscan.app.data.recentDocumentsDataStore
import org.fairscan.app.platform.AndroidPdfWriter
import org.fairscan.app.platform.OpenCvTransformations
import org.fairscan.app.ui.NavigationState
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.state.PdfGenerationUiState
import org.fairscan.app.ui.state.RecentDocumentUiState
import java.io.File

const val THUMBNAIL_SIZE_DP = 120

class MainViewModel(
    private val imageRepository: ImageRepository,
    private val pdfFileManager: PdfFileManager,
    private val recentDocumentsDataStore: DataStore<RecentDocuments>,
): ViewModel() {

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val density = context.resources.displayMetrics.density
                val thumbnailSizePx = (THUMBNAIL_SIZE_DP * density).toInt()
                return MainViewModel(
                    ImageRepository(context.filesDir, OpenCvTransformations(), thumbnailSizePx),
                    PdfFileManager(
                        File(context.cacheDir, "pdfs"),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        AndroidPdfWriter()
                    ),
                    context.recentDocumentsDataStore,
                ) as T
            }
        }
    }

    private val _navigationState = MutableStateFlow(NavigationState.initial())
    val currentScreen: StateFlow<Screen> = _navigationState.map { it.current }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _navigationState.value.current)

    private val _pageIds = MutableStateFlow(imageRepository.imageIds())
    val documentUiModel: StateFlow<DocumentUiModel> =
        _pageIds.map { ids ->
            DocumentUiModel(
                pageIds = ids,
                imageLoader = ::getBitmap,
                thumbnailLoader = ::getThumbnail,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DocumentUiModel(persistentListOf(), ::getBitmap, ::getThumbnail)
        )

    fun navigateTo(destination: Screen) {
        _navigationState.update { it.navigateTo(destination) }
    }

    fun navigateBack() {
        _navigationState.update { stack -> stack.navigateBack() }
    }

    fun rotateImage(id: String, clockwise: Boolean) {
        viewModelScope.launch {
            imageRepository.rotate(id, clockwise)
            _pageIds.value = imageRepository.imageIds()
        }
    }

    fun movePage(id: String, newIndex: Int) {
        imageRepository.movePage(id, newIndex)
        _pageIds.value = imageRepository.imageIds()
    }

    fun deletePage(id: String) {
        imageRepository.delete(id)
        _pageIds.value = imageRepository.imageIds()
    }

    fun startNewDocument() {
        _pageIds.value = persistentListOf()
        viewModelScope.launch {
            imageRepository.clear()
        }
    }

    fun getBitmap(id: String): Bitmap? {
        val bytes = imageRepository.getContent(id)
        return bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    fun getThumbnail(id: String): Bitmap? {
        val bytes = imageRepository.getThumbnail(id)
        return bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

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

    fun cleanUpOldPdfs(thresholdInMillis: Int) {
        pdfFileManager.cleanUpOldFiles(thresholdInMillis)
    }


    val recentDocuments: StateFlow<List<RecentDocumentUiState>> =
        recentDocumentsDataStore.data.map {
            it.documentsList.map {
                doc ->
                    RecentDocumentUiState(
                        file = File(doc.filePath),
                        saveTimestamp = doc.createdAt,
                        pageCount = doc.pageCount,
                    )
            }.filter { doc -> doc.file.exists() }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    fun addRecentDocument(filePath: String, pageCount: Int) {
        viewModelScope.launch {
            recentDocumentsDataStore.updateData { current ->
                val newDoc = RecentDocument.newBuilder()
                    .setFilePath(filePath)
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

    fun handleImageCaptured(jpegBytes: ByteArray) {
        imageRepository.add(jpegBytes)
        _pageIds.value = imageRepository.imageIds()
    }
}

// TODO Move somewhere else: ViewModel should not depend on that
data class PdfGenerationActions(
    val startGeneration: () -> Unit,
    val setFilename: (String) -> Unit,
    val uiStateFlow: StateFlow<PdfGenerationUiState>,// TODO is it ok to have that here?
    val sharePdf: () -> Unit,
    val savePdf: () -> Unit,
    val openPdf: () -> Unit,
)
