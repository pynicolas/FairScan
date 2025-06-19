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

import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.util.concurrent.ListenableFuture
import org.mydomain.myscan.LiveAnalysisState
import org.mydomain.myscan.MainViewModel
import org.mydomain.myscan.Point
import org.mydomain.myscan.scaledTo
import org.mydomain.myscan.ui.theme.MyScanTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// TODO Split this big file

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    liveAnalysisState: LiveAnalysisState,
    onImageAnalyzed: (ImageProxy) -> Unit,
    onFinalizePressed: () -> Unit,
    modifier: Modifier,
) {
    // TODO pause the live analysis when displaying the PageValidationDialogs
    val showPageDialog = rememberSaveable { mutableStateOf(false) }
    val isProcessing = rememberSaveable { mutableStateOf(false) }
    val pageToValidate by viewModel.pageToValidate.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "Camera permission was denied", Toast.LENGTH_SHORT).show()
        }
    }

    val captureController = remember { CameraCaptureController() }
    DisposableEffect(Unit) {
        onDispose { captureController.shutdown() }
    }

    LaunchedEffect(Unit) {
        val camera = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, camera) != PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(camera)
        }
    }

    CameraScreenContent(
        modifier,
        cameraPreview = {
            CameraPreview(
                onImageAnalyzed = onImageAnalyzed,
                captureController = captureController
            ) },
        pageCount = viewModel.pageCount(),
        liveAnalysisState,
        onCapture = {
            viewModel.liveAnalysisEnabled = false
            showPageDialog.value = true
            isProcessing.value = true
            captureController.takePicture(
                onImageCaptured = { imageProxy ->
                    if (imageProxy != null) {
                        viewModel.processCapturedImageThen(imageProxy) {
                            isProcessing.value = false
                            viewModel.liveAnalysisEnabled = true
                        }
                    } else {
                        Log.e("MyScan", "Error during image capture")
                    }
                }
            )
        },
        onFinalizePressed = onFinalizePressed
    )

    if (showPageDialog.value) {
        PageValidationDialog(
            isProcessing = isProcessing.value,
            pageBitmap = pageToValidate,
            onConfirm = {
                pageToValidate?.let { viewModel.addPage(it) }
                showPageDialog.value = false
            },
            onReject = {
                showPageDialog.value = false
            },
            onDismiss = {
                showPageDialog.value = false
            }
        )
    }
}

@Composable
private fun CameraScreenContent(
    modifier: Modifier,
    cameraPreview: @Composable () -> Unit,
    pageCount: Int,
    liveAnalysisState: LiveAnalysisState,
    onCapture: () -> Unit,
    onFinalizePressed: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        CameraPreviewWithOverlay(cameraPreview, liveAnalysisState)
        MessageBox(liveAnalysisState.inferenceTime)

        CaptureButton(
            onClick = onCapture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
        CameraScreenFooter(
            pageCount = pageCount,
            onFinalizePressed = onFinalizePressed,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun CaptureButton(onClick: () -> Unit, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(80.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(
                    width = 4.dp,
                    color = color.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(color = color, shape = CircleShape)
        )
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    cameraPreview: @Composable () -> Unit,
    liveAnalysisState: LiveAnalysisState
) {
    val width = LocalConfiguration.current.screenWidthDp
    val height = width / 3 * 4
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
    ) {
        cameraPreview()
        AnalysisOverlay(liveAnalysisState)
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageAnalyzed: (ImageProxy) -> Unit,
    captureController: CameraCaptureController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture by remember {
        mutableStateOf(ProcessCameraProvider.getInstance(context))
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }

    AndroidView(modifier = modifier, factory = {
        val previewView = PreviewView(it).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FIT_CENTER
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
private fun AnalysisOverlay(liveAnalysisState: LiveAnalysisState) {
    val binaryMask = liveAnalysisState.binaryMask
    if (binaryMask == null) {
        return
    }
    val maskOverlay = replaceColor(binaryMask, Color.Black, Color.Transparent)
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawImage(
            maskOverlay.scale(size.width.toInt(), size.height.toInt()).asImageBitmap(),
            colorFilter = ColorFilter.tint(Color(0x8000FF00), BlendMode.SrcIn)
        )
        if (liveAnalysisState.documentQuad != null) {
            val scaledQuad = liveAnalysisState.documentQuad.scaledTo(
                fromWidth = binaryMask.width,
                fromHeight = binaryMask.height,
                toWidth = size.width.toInt(),
                toHeight = size.height.toInt()
            )
            scaledQuad.edges().forEach {
                drawLine(Color.Green, it.from.toOffset(), it.to.toOffset(), 5.0f)
            }
        }
    }
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

@Composable
fun MessageBox(inferenceTime: Long) {
    Text(
        text = if(inferenceTime == 0L) "" else "Segmentation time: $inferenceTime ms",
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        color = Color.Gray,
    )
}

@Composable
fun CameraScreenFooter(
    pageCount: Int,
    onFinalizePressed: () -> Unit,
    modifier: Modifier,
) {
    Surface (
        color = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth().height(56.dp)
    ) {
        Row (
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Pages : $pageCount",
                style = MaterialTheme.typography.bodyMedium
            )

            Button (
                onClick = onFinalizePressed,
                enabled = pageCount > 0
            ) {
                Text("Finish")
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    MyScanTheme {
        CameraScreenContent(
            modifier = Modifier,
            cameraPreview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera Preview", color = Color.White)
                }
            },
            pageCount = 3,
            liveAnalysisState = LiveAnalysisState(),
            onCapture = {},
            onFinalizePressed = {})
    }
}
