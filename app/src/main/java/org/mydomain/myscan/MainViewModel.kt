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
package org.mydomain.myscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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
import org.mydomain.myscan.ui.PdfGenerationUiState
import org.mydomain.myscan.view.DocumentUiModel
import java.io.ByteArrayOutputStream
import java.io.File

class MainViewModel(
    private val imageSegmentationService: ImageSegmentationService,
    private val imageRepository: ImageRepository,
    private val pdfFileManager: PdfFileManager,
): ViewModel() {

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MainViewModel(
                    ImageSegmentationService(context),
                    ImageRepository(context.filesDir),
                    PdfFileManager(
                        File(context.cacheDir, "pdfs"),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        AndroidPdfWriter()),
                ) as T
            }
        }
    }

    private var _liveAnalysisState = MutableStateFlow(LiveAnalysisState())
    val liveAnalysisState: StateFlow<LiveAnalysisState> = _liveAnalysisState.asStateFlow()
    private var lastSuccessfulLiveAnalysisState: LiveAnalysisState? = null

    private val _screenStack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val currentScreen: StateFlow<Screen> = _screenStack.map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _screenStack.value.last())

    private val _pageIds = MutableStateFlow(imageRepository.imageIds())
    val documentUiModel: StateFlow<DocumentUiModel> =
        _pageIds.map { ids ->
            DocumentUiModel(
                pageIds = ids,
                imageLoader = ::getBitmap
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DocumentUiModel(emptyList(), ::getBitmap)
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

    fun navigateTo(screen: Screen) {
        _screenStack.update { it + screen }
    }

    fun navigateBack() {
        _screenStack.update { stack -> if (stack.size > 1) stack.dropLast(1) else stack }
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

    fun afterCaptureError() {
        _captureState.value = CaptureState.Idle
    }

    fun deletePage(id: String) {
        imageRepository.delete(id)
        _pageIds.value = imageRepository.imageIds()
    }

    fun startNewDocument() {
        _pageIds.value = listOf()
        viewModelScope.launch {
            imageRepository.clear()
        }
    }

    fun getBitmap(id: String): Bitmap? {
        val bytes = imageRepository.getContent(id)
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
        val currentState = _pdfUiState.value
        if (currentState.isGenerating || currentState.generatedPdf != null) return

        _pdfUiState.update { it.copy(isGenerating = true, errorMessage = null) }

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
                Log.e("MyScan", "PDF generation failed", e)
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

    fun getFinalPdf(): GeneratedPdf? {
        val tempPdf = _pdfUiState.value.generatedPdf ?: return null
        val tempFile = tempPdf.file
        val newFile = File(tempFile.parentFile, desiredFilename)
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
        val dirName = copiedFile.parentFile?.name
        _pdfUiState.update { it.copy(savedFileUri = pdfFile.toUri(), saveDirectoryName = dirName) }
        return copiedFile
    }

    fun cleanUpOldPdfs(thresholdInMillis: Int) {
        pdfFileManager.cleanUpOldFiles(thresholdInMillis)
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
    val cancelGeneration: () -> Unit,
    val setFilename: (String) -> Unit,
    val uiStateFlow: StateFlow<PdfGenerationUiState>,// TODO is it ok to have that here?
    val sharePdf: () -> Unit,
    val savePdf: () -> Unit,
    val openPdf: () -> Unit,
)
