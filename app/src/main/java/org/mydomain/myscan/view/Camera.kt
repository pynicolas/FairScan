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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.google.common.util.concurrent.ListenableFuture
import org.mydomain.myscan.CameraScreenState
import org.mydomain.myscan.MainViewModel
import org.mydomain.myscan.Point
import org.mydomain.myscan.scaledTo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    uiState: CameraScreenState,
    onImageAnalyzed: (ImageProxy) -> Unit,
) {
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

    Column {
        val width = LocalConfiguration.current.screenWidthDp
        val height = width / 3 * 4
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
        ) {
            CameraPreview(
                onImageAnalyzed = onImageAnalyzed,
                captureController = captureController)
            AnalysisOverlay(uiState)
        }
        MessageBox(uiState.inferenceTime)
        Button(
            onClick = {
                captureController.takePicture(
                    onImageCaptured = { imageProxy ->
                        if (imageProxy != null) {
                            viewModel.processCapturedImageAndNavigate(imageProxy)
                        } else {
                            Log.e("MyScan", "Error during image capture")
                        }
                    }
                )},
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Capture")
        }
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
private fun AnalysisOverlay(cameraScreenState: CameraScreenState) {
    val binaryMask = cameraScreenState.binaryMask
    if (binaryMask == null) {
        return
    }
    val maskOverlay = replaceColor(binaryMask, Color.Black, Color.Transparent)
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawImage(
            maskOverlay.scale(size.width.toInt(), size.height.toInt()).asImageBitmap(),
            colorFilter = ColorFilter.tint(Color(0x8000FF00), BlendMode.SrcIn)
        )
        if (cameraScreenState.documentQuad != null) {
            val scaledQuad = cameraScreenState.documentQuad.scaledTo(
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
