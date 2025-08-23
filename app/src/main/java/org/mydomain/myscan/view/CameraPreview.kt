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
package org.mydomain.myscan.view

import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import org.mydomain.myscan.CameraPermissionState
import org.mydomain.myscan.LiveAnalysisState
import org.mydomain.myscan.Point
import org.mydomain.myscan.scaledTo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageAnalyzed: (ImageProxy) -> Unit,
    captureController: CameraCaptureController,
    onPreviewViewReady: (PreviewView) -> Unit,
    cameraPermission: CameraPermissionState,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!cameraPermission.isGranted) {
            cameraPermission.request()
        }
    }

    val cameraProviderFuture by remember {
        mutableStateOf(ProcessCameraProvider.getInstance(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }

    AndroidView(modifier = modifier, factory = {
        val previewView = PreviewView(it).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FIT_CENTER
            onPreviewViewReady(this)
        }
        val executor = Executors.newSingleThreadExecutor()
        cameraProviderFuture.addListener({
            bindCameraUseCases(
                lifecycleOwner = lifecycleOwner,
                cameraProviderFuture = cameraProviderFuture,
                executor = executor,
                previewView = previewView,
                onImageAnalyzed = onImageAnalyzed,
                captureController = captureController
            )
        }, ContextCompat.getMainExecutor(context))

        previewView
    })
}

fun bindCameraUseCases(
    lifecycleOwner: LifecycleOwner,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    executor: ExecutorService,
    previewView: PreviewView,
    onImageAnalyzed: (ImageProxy) -> Unit,
    captureController: CameraCaptureController,
) {
    val ratio_4_3 = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .build()
    val preview: Preview = Preview.Builder().setResolutionSelector(ratio_4_3).build()
    preview.surfaceProvider = previewView.surfaceProvider

    val cameraSelector: CameraSelector =
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
    val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(ratio_4_3)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
    imageAnalysis.setAnalyzer(executor, onImageAnalyzed)

    val imageCapture = ImageCapture.Builder()
        .setResolutionSelector(ratio_4_3)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
    captureController.imageCapture = imageCapture

    val cameraProvider = cameraProviderFuture.get()
    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis, preview, imageCapture)
}

@Composable
fun AnalysisOverlay(liveAnalysisState: LiveAnalysisState, debugMode: Boolean) {
    val binaryMask = liveAnalysisState.binaryMask
    if (binaryMask == null) {
        return
    }
    val quadColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (debugMode) {
            drawMask(this, binaryMask)
        }
        if (liveAnalysisState.documentQuad != null) {
            val scaledQuad = liveAnalysisState.documentQuad.scaledTo(
                fromWidth = binaryMask.width,
                fromHeight = binaryMask.height,
                toWidth = size.width.toInt(),
                toHeight = size.height.toInt()
            )
            scaledQuad.edges().forEach {
                drawLine(quadColor, it.from.toOffset(), it.to.toOffset(), 10.0f)
            }
        }
    }
}

private fun drawMask(drawScope: DrawScope, binaryMask: Bitmap) {
    val maskOverlay = replaceColor(binaryMask, Color.Black, Color.Transparent)
    val size = drawScope.size
    drawScope.drawImage(
        maskOverlay.scale(size.width.toInt(), size.height.toInt()).asImageBitmap(),
        colorFilter = ColorFilter.tint(Color(0x8000FF00), BlendMode.SrcIn)
    )
}

fun replaceColor(bitmap: Bitmap, toReplace: Color, replacement: Color): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

    val pixels = IntArray(width * height)
    result.getPixels(pixels, 0, width, 0, 0, width, height)

    val target = toReplace.toArgb()
    val newColor = replacement.toArgb()

    for (i in pixels.indices) {
        if (pixels[i] == target) {
            pixels[i] = newColor
        }
    }

    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
}

fun Point.toOffset() = Offset(x.toFloat(), y.toFloat())

class CameraCaptureController {
    var imageCapture: ImageCapture? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun shutdown() {
        executor.shutdown()
    }

    fun takePicture(onImageCaptured: (ImageProxy?) -> Unit) {
        imageCapture?.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    onImageCaptured(imageProxy)
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraCapture", "Image capture failed: ${exception.message}", exception)
                    onImageCaptured(null)
                }
            }
        )
    }
}
