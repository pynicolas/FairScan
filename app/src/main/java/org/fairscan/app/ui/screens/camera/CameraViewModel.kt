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
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.AppContainer
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation
import org.fairscan.imageprocessing.Mask
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.isColoredDocument
import org.fairscan.imageprocessing.scaledTo
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

sealed interface CameraEvent {
    data class ImageCaptured(val page: CapturedPage) : CameraEvent
}

class CameraViewModel(appContainer: AppContainer): ViewModel() {

    private val imageSegmentationService = appContainer.imageSegmentationService
    private val logger = appContainer.logger

    private val _events = MutableSharedFlow<CameraEvent>()
    val events = _events.asSharedFlow()

    private var _liveAnalysisState = MutableStateFlow(LiveAnalysisState())
    val liveAnalysisState: StateFlow<LiveAnalysisState> = _liveAnalysisState.asStateFlow()
    private var quadStabilizer = QuadStabilizer()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState

    init {
        viewModelScope.launch {
            imageSegmentationService.initialize()
            imageSegmentationService.segmentation
                .filterNotNull()
                .collect { result ->
                    // TODO Should we really call toBinaryMask if it's used only in debug mode?
                    val binaryMask = result.segmentation.toBinaryMask()
                    val rawQuad = detectDocumentQuad(
                        result.segmentation,
                        isLiveAnalysis = true
                    )
                    val stableQuad = quadStabilizer.update(rawQuad)
                    _liveAnalysisState.value = LiveAnalysisState(
                        inferenceTime = result.inferenceTime,
                        binaryMask = binaryMask,
                        documentQuad = rawQuad,
                        stableQuad = stableQuad,
                    )
                }
        }
    }

    fun onCapturePressed(frozenImage: Bitmap) {
        _captureState.value = CaptureState.Capturing(frozenImage)
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
        rotationDegrees: Int
    ): CapturedPage? = withContext(Dispatchers.IO) {
        var result: CapturedPage? = null
        val segmentation = imageSegmentationService.runSegmentationAndReturn(source, 0)
        if (segmentation != null) {
            val mask = segmentation.segmentation
            val quad = detectDocumentQuad(mask, isLiveAnalysis = false)
            if (quad != null) {
                val resizedQuad = quad.scaledTo(mask.width, mask.height, source.width, source.height)
                result = extractDocumentFromBitmap(source, resizedQuad, rotationDegrees, mask)
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

fun extractDocumentFromBitmap(
    source: Bitmap, quad: Quad, rotationDegrees: Int, mask: Mask
): CapturedPage {
    val rgba = Mat()
    Utils.bitmapToMat(source, rgba)
    val bgr = Mat()
    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR) // CV_8UC4 → CV_8UC3
    rgba.release()
    val isColored = isColoredDocument(bgr, mask, quad)
    val maxPixels = ExportQuality.BALANCED.maxPixels
    val page = extractDocument(bgr, quad, rotationDegrees, isColored, maxPixels)
    val outBgr = page
    bgr.release()
    val outBitmap = toBitmap(outBgr)
    outBgr.release()
    val normalizedQuad = quad.scaledTo(source.width, source.height, 1, 1)
    val baseRotation = Rotation.fromDegrees(rotationDegrees)
    val metadata = PageMetadata(normalizedQuad, baseRotation, isColored)
    return CapturedPage(outBitmap, source, metadata)
}

fun toBitmap(bgr: Mat): Bitmap {
    require(bgr.type() == CvType.CV_8UC3)

    val rgba = Mat()
    Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA)

    val bmp = createBitmap(bgr.cols(), bgr.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(rgba, bmp)

    rgba.release()
    return bmp
}
