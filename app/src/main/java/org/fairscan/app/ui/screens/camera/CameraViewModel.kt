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
package org.fairscan.app.ui.screens.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.AppContainer
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.platform.extractDocumentFromBitmap
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.detectDocumentQuad

sealed interface CameraEvent {
    data class ImageCaptured(val page: CapturedPage) : CameraEvent
}

class CameraViewModel(appContainer: AppContainer): ViewModel() {

    private val imageSegmentationService = appContainer.imageSegmentationService
    private val settingsRepository = appContainer.settingsRepository
    private val imageLoader = appContainer.imageLoader
    private val logger = appContainer.logger

    private val _events = MutableSharedFlow<CameraEvent>()
    val events = _events.asSharedFlow()

    private var _liveAnalysisState = MutableStateFlow(LiveAnalysisState())
    val liveAnalysisState: StateFlow<LiveAnalysisState> = _liveAnalysisState.asStateFlow()
    private var quadStabilizer = QuadStabilizer()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    private val _isTorchEnabled = MutableStateFlow(false)
    val isTorchEnabled: StateFlow<Boolean> = _isTorchEnabled

    init {
        viewModelScope.launch {
            imageSegmentationService.initialize()
        }
    }

    fun resetLiveAnalysis() {
        quadStabilizer = QuadStabilizer()
        _liveAnalysisState.value = LiveAnalysisState()
    }

    fun onCapturePressed(frozenImage: Bitmap) {
        _captureState.value = CaptureState.Capturing(frozenImage)
        resetLiveAnalysis()
    }

    private fun onCaptureProcessed(captured: CapturedPage?) {
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
        if (_captureState.value !is CaptureState.Idle || _importState.value !is ImportState.Idle) {
            imageProxy.close()
            return
        }

        viewModelScope.launch {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val result = withContext(Dispatchers.IO) {
                imageSegmentationService.runSegmentationAndReturn(imageProxy.toBitmap())
            }

            result?.let {
                val segmentation = result.segmentation
                val maskSize = segmentation.maskSize()
                val originalSize = ImageSize(imageProxy.width, imageProxy.height)
                val rawQuad = withContext(Dispatchers.Default) {
                    detectDocumentQuad(segmentation, originalSize, isLiveAnalysis = true)
                        ?.rotate90(rotationDegrees / 90, maskSize)
                }
                val binaryMaskProvider = { ->
                    var binaryMask: Bitmap = segmentation.toBinaryMask()
                    if (rotationDegrees != 0) {
                        binaryMask = rotateBitmap(binaryMask, rotationDegrees.toFloat())
                    }
                    binaryMask
                }
                val stableQuad = quadStabilizer.update(rawQuad)
                _liveAnalysisState.value = LiveAnalysisState(
                    inferenceTime = result.inferenceTime,
                    binaryMaskProvider = binaryMaskProvider,
                    maskSize = maskSize,
                    stableQuad = stableQuad,
                )
            }

            imageProxy.close()
        }
    }

    fun onImageCaptured(imageProxy: ImageProxy?) {
        if (imageProxy != null) {
            viewModelScope.launch {
                try {
                    val source = imageProxy.toBitmap()
                    val page = processCapturedImage(source, imageProxy.imageInfo.rotationDegrees)
                    imageProxy.close()
                    onCaptureProcessed(page)
                } catch (e: RuntimeException) {
                    logger.e("Camera", "Failed to process captured image", e)
                    onCaptureProcessed(null)
                }
            }
        } else {
            onCaptureProcessed(null)
        }
    }

    private suspend fun processCapturedImage(
        source: Bitmap,
        rotationDegrees: Int,
    ): CapturedPage? = withContext(Dispatchers.IO) {
        var result: CapturedPage? = null
        val segmentation = imageSegmentationService.runSegmentationAndReturn(source)
        if (segmentation != null) {
            val mask = segmentation.segmentation
            val originalSize = ImageSize(source.width, source.height)
            val quad = detectDocumentQuad(mask, originalSize, isLiveAnalysis = false)
            if (quad != null) {
                val defaultColorMode = settingsRepository.defaultColorMode.first()
                result = extractDocumentFromBitmap(
                    source, quad, rotationDegrees, mask, viewModelScope, defaultColorMode)
            }
        }
        return@withContext result
    }

    fun addProcessedImage() {
        val current = _captureState.value
        if (current is CaptureState.CapturePreview) {
            viewModelScope.launch {
                _events.emit(CameraEvent.ImageCaptured(current.capturedPage))
            }
        }
        _captureState.value = CaptureState.Idle
    }

    fun afterCaptureError() {
        _captureState.value = CaptureState.Idle
    }

    fun logError(message:String, throwable: Throwable) {
        viewModelScope.launch {
            logger.e("Camera", message, throwable)
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        _isTorchEnabled.value = enabled
    }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _importState.value = ImportState.Idle
            return
        }
        viewModelScope.launch {
            _importState.value = ImportState.Importing(0, uris.size)
            uris.forEachIndexed { index, uri ->
                val photoToImport = imageLoader.load(uri)
                val page = processCapturedImage(photoToImport, 0)
                page?.let {
                    _events.emit(CameraEvent.ImageCaptured(it))
                }
                _importState.value = ImportState.Importing(index + 1, uris.size)
            }
            _importState.value = ImportState.Idle
        }
    }

    fun onImportClicked() {
        _importState.value = ImportState.Selecting
        resetLiveAnalysis()
    }
}

sealed class CaptureState {
    open val frozenImage: Bitmap? = null

    object Idle : CaptureState()
    data class Capturing(override val frozenImage: Bitmap) : CaptureState()
    data class CaptureError(override val frozenImage: Bitmap) : CaptureState()
    data class CapturePreview(
        override val frozenImage: Bitmap,
        val capturedPage: CapturedPage,
    ) : CaptureState()
}

fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true)
}
