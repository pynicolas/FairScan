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
package org.fairscan.app.ui.screens.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.AppContainer
import org.fairscan.app.domain.detectDocumentQuad
import org.fairscan.app.domain.extractDocument
import org.fairscan.app.domain.scaledTo
import org.fairscan.app.ui.state.LiveAnalysisState
import java.io.ByteArrayOutputStream

sealed interface CameraEvent {
    data class ImageCaptured(val jpegBytes: ByteArray) : CameraEvent
}

class CameraViewModel(appContainer: AppContainer): ViewModel() {

    private val imageSegmentationService = appContainer.imageSegmentationService

    private val _events = MutableSharedFlow<CameraEvent>()
    val events = _events.asSharedFlow()

    private var _liveAnalysisState = MutableStateFlow(LiveAnalysisState())
    val liveAnalysisState: StateFlow<LiveAnalysisState> = _liveAnalysisState.asStateFlow()
    private var lastSuccessfulLiveAnalysisState: LiveAnalysisState? = null

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState

    init {
        viewModelScope.launch {
            imageSegmentationService.initialize()
            imageSegmentationService.segmentation
                .filterNotNull()
                .map {
                    // TODO Should we really call toBinaryMask if it's used only in debug mode?
                    val binaryMask = it.segmentation.toBinaryMask()
                    LiveAnalysisState(
                        inferenceTime = it.inferenceTime,
                        binaryMask = binaryMask,
                        documentQuad = detectDocumentQuad(it.segmentation, isLiveAnalysis = true),
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
            val mask = segmentation.segmentation
            var quad = detectDocumentQuad(mask, isLiveAnalysis = false)
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
            viewModelScope.launch {
                _events.emit(CameraEvent.ImageCaptured(jpegBytes))
            }
        }
        _captureState.value = CaptureState.Idle
    }

    fun afterCaptureError() {
        _captureState.value = CaptureState.Idle
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
