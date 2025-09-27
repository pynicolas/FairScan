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
import androidx.camera.core.ImageProxy
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.data.recentDocumentsDataStore
import org.fairscan.app.ui.PdfGenerationUiState
import org.fairscan.app.ui.RecentDocumentUiState
import org.fairscan.app.view.DocumentUiModel
import java.io.ByteArrayOutputStream
import java.io.File

const val THUMBNAIL_SIZE_DP = 120

class MainViewModel(
    private val imageSegmentationService: ImageSegmentationService,
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
                    ImageSegmentationService(context),
                    ImageRepository(context.filesDir, OpenCvTransformations(), thumbnailSizePx),
                    PdfFileManager(
                        File(context.cacheDir, "pdfs"),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        AndroidPdfWriter()),
                    context.recentDocumentsDataStore,
                ) as T
            }
        }
    }

    private var _liveAnalysisState = MutableStateFlow(LiveAnalysisState())
    val liveAnalysisState: StateFlow<LiveAnalysisState> = _liveAnalysisState.asStateFlow()
    private var lastSuccessfulLiveAnalysisState: LiveAnalysisState? = null

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

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState

    init {
        viewModelScope.launch {
            imageSegmentationService.initialize()
            imageSegmentationService.segmentation
                .filterNotNull()
                .map {
                    val binaryMask = it.segmentation.toBinaryMask()
                    LiveAnalysisState(
                        inferenceTime = it.inferenceTime,
                        binaryMask = binaryMask,
                        documentQuad = detectDocumentQuad(binaryMask),
                        timestamp = System.currentTimeMillis(),
                    )
                }
                .collect {
                    _liveAnalysisState.value = it
                    if (it.documentQuad != null) {
                        lastSuccessfulLiveAnalysisState = it
                    }
                }
        }
    }

    sealed class CaptureState {
        open val frozenImage: Bitmap? = null

        object Idle : CaptureState()
        data class Capturing(override val frozenImage: Bitmap) : CaptureState()
        data class CaptureError(override val frozenImage: Bitmap) : CaptureState()
        data class CapturePreview(
            override val frozenImage: Bitmap,
            val processed: Bitmap
        ) : CaptureState()
    }


    fun onCapturePressed(frozenImage: Bitmap) {
        _captureState.value = CaptureState.Capturing(frozenImage)
    }

    private fun onCaptureProcessed(captured: Bitmap?) {
        val current = _captureState.value
        _captureState.value = when {
            current is CaptureState.Capturing && captured != null ->
                CaptureState.CapturePreview(current.frozenImage, captured)
            current is CaptureState.Capturing ->
                CaptureState.CaptureError(current.frozenImage)
            else -> CaptureState.Idle
        }
    }

    fun liveAnalysis(imageProxy: ImageProxy) {
        if (_captureState.value !is CaptureState.Idle) {
            imageProxy.close()
            return
        }

        viewModelScope.launch {
            imageSegmentationService.runSegmentationAndEmit(
                imageProxy.toBitmap(),
                imageProxy.imageInfo.rotationDegrees,
            )
            imageProxy.close()
        }
    }

    fun navigateTo(destination: Screen) {
        _navigationState.update { it.navigateTo(destination) }
    }

    fun navigateBack() {
        _navigationState.update { stack -> stack.navigateBack() }
    }

    fun onImageCaptured(imageProxy: ImageProxy?) {
        if (imageProxy != null) {
            viewModelScope.launch {
                val image = processCapturedImage(imageProxy)
                imageProxy.close()
                onCaptureProcessed(image)
            }
        } else {
            onCaptureProcessed(null)
        }
    }

    private suspend fun processCapturedImage(imageProxy: ImageProxy): Bitmap? = withContext(Dispatchers.IO) {
        var corrected: Bitmap? = null
        val bitmap = imageProxy.toBitmap()
        val segmentation = imageSegmentationService.runSegmentationAndReturn(bitmap, 0)
        if (segmentation != null) {
            val mask = segmentation.segmentation.toBinaryMask()
            var quad = detectDocumentQuad(mask)
            if (quad == null) {
                val now = System.currentTimeMillis()
                lastSuccessfulLiveAnalysisState?.timestamp?.let {
                    val offset = now - it
                    Log.i("Quad", "Last successful live analysis was $offset ms ago")
                }
                val recentLive = lastSuccessfulLiveAnalysisState?.takeIf {
                    now - it.timestamp <= 1500
                }
                val rotations = (-imageProxy.imageInfo.rotationDegrees / 90) + 4
                quad = recentLive?.documentQuad?.rotate90(rotations, mask.width, mask.height)
                if (quad != null) {
                    Log.i("Quad", "Using quad taken in live analysis; rotations=$rotations")
                }
            }
            if (quad != null) {
                val resizedQuad = quad.scaledTo(mask.width, mask.height, bitmap.width, bitmap.height)
                corrected = extractDocument(bitmap, resizedQuad, imageProxy.imageInfo.rotationDegrees)
            }
        }
        return@withContext corrected
    }

    fun addProcessedImage(quality: Int = 75) {
        val current = _captureState.value
        if (current is CaptureState.CapturePreview) {
            val outputStream = ByteArrayOutputStream()
            current.processed.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val jpegBytes = outputStream.toByteArray()
            imageRepository.add(jpegBytes)
            _pageIds.value = imageRepository.imageIds()
        }
        _captureState.value = CaptureState.Idle
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

    fun afterCaptureError() {
        _captureState.value = CaptureState.Idle
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
}

data class GeneratedPdf(
    val file: File,
    val sizeInBytes: Long,
    val pageCount: Int,
)

// TODO Move somewhere else: ViewModel should not depend on that
data class PdfGenerationActions(
    val startGeneration: () -> Unit,
    val setFilename: (String) -> Unit,
    val uiStateFlow: StateFlow<PdfGenerationUiState>,// TODO is it ok to have that here?
    val sharePdf: () -> Unit,
    val savePdf: () -> Unit,
    val openPdf: () -> Unit,
)
