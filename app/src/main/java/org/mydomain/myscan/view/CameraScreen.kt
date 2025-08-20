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

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.mydomain.myscan.LiveAnalysisState
import org.mydomain.myscan.MainViewModel
import org.mydomain.myscan.MainViewModel.CaptureState
import org.mydomain.myscan.R
import org.mydomain.myscan.Screen
import org.mydomain.myscan.ui.theme.MyScanTheme

data class CameraUiState(
    val pageCount: Int,
    val liveAnalysisState: LiveAnalysisState,
    val captureState: CaptureState,
    val showDetectionError: Boolean,
    val isLandscape: Boolean,
    val isDebugMode: Boolean
)

const val CAPTURED_IMAGE_DISPLAY_DURATION = 1500L
const val ANIMATION_DURATION = 200

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    liveAnalysisState: LiveAnalysisState,
    onImageAnalyzed: (ImageProxy) -> Unit,
    onFinalizePressed: () -> Unit,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val document by viewModel.documentUiModel.collectAsStateWithLifecycle()
    val thumbnailCoords = remember { mutableStateOf(Offset.Zero) }
    var isDebugMode by remember { mutableStateOf(false) }

    val captureController = remember { CameraCaptureController() }
    DisposableEffect(Unit) {
        onDispose { captureController.shutdown() }
    }

    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    if (captureState is CaptureState.CapturePreview) {
        LaunchedEffect(captureState) {
            delay(CAPTURED_IMAGE_DISPLAY_DURATION)
            viewModel.addProcessedImage()
        }
    }

    var showDetectionError by remember { mutableStateOf(false) }
    LaunchedEffect(captureState) {
        if (captureState is CaptureState.CaptureError) {
            showDetectionError = true
            delay(1000)
            showDetectionError = false
            viewModel.afterCaptureError()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(document.pageCount()) {
        if (!document.isEmpty()) {
            listState.animateScrollToItem(document.lastIndex())
        }
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    CameraScreenScaffold(
        cameraPreview = {
            CameraPreview(
                onImageAnalyzed = onImageAnalyzed,
                captureController = captureController,
                onPreviewViewReady = { view -> previewView = view }
            )
        },
        pageListState =
            CommonPageListState(
                document = document,
                onPageClick = { index -> viewModel.navigateTo(Screen.Document(index)) },
                listState = listState,
                onLastItemPosition = { offset -> thumbnailCoords.value = offset },
            ),
        cameraUiState = CameraUiState(
            document.pageCount(),
            liveAnalysisState,
            captureState,
            showDetectionError,
            isLandscape = isLandscape,
            isDebugMode),
        onCapture = {
            previewView?.bitmap?.let {
                Log.i("MyScan", "Pressed <Capture>")
                viewModel.onCapturePressed(it)
                captureController.takePicture(
                    onImageCaptured = { imageProxy -> viewModel.onImageCaptured(imageProxy) }
                )
            }
        },
        onFinalizePressed = onFinalizePressed,
        onDebugModeSwitched = { isDebugMode = !isDebugMode },
        thumbnailCoords = thumbnailCoords,
        toAboutScreen = { viewModel.navigateTo(Screen.About) }
    )
}

@Composable
private fun CameraScreenScaffold(
    cameraPreview: @Composable () -> Unit,
    pageListState: CommonPageListState,
    cameraUiState: CameraUiState,
    onCapture: () -> Unit,
    onFinalizePressed: () -> Unit,
    onDebugModeSwitched: () -> Unit,
    thumbnailCoords: MutableState<Offset>,
    toAboutScreen: () -> Unit,
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val tapThreshold = 500L
    val onPageCountClick = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime < tapThreshold) {
            tapCount++
            if (tapCount >= 3) {
                onDebugModeSwitched()
                tapCount = 0
            }
        } else {
            tapCount = 1
        }
        lastTapTime = currentTime
    }

    Box {
        MyScaffold(
            toAboutScreen = toAboutScreen,
            pageListState = pageListState,
            bottomBar = { Bar(cameraUiState.pageCount, onPageCountClick, onFinalizePressed) }
        ) {
            modifier -> CameraPreviewBox(cameraPreview, cameraUiState, onCapture, modifier)
        }
        if (cameraUiState.captureState is CaptureState.CapturePreview) {
            CapturedImage(cameraUiState.captureState.processed.asImageBitmap(), thumbnailCoords)
        }
    }
}

@Composable
private fun CameraPreviewBox(
    cameraPreview: @Composable (() -> Unit),
    cameraUiState: CameraUiState,
    onCapture: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
    ) {
        CameraPreviewWithOverlay(
            cameraPreview,
            cameraUiState,
            Modifier.align(Alignment.BottomCenter)
        )
        if (cameraUiState.isDebugMode) {
            MessageBox(cameraUiState.liveAnalysisState.inferenceTime)
        }
        CaptureButton(
            onClick = onCapture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun CapturedImage(image: ImageBitmap, thumbnailCoords: MutableState<Offset>) {
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxSize(),
    ) {}

    var isAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(image) {
        delay(CAPTURED_IMAGE_DISPLAY_DURATION - ANIMATION_DURATION)
        isAnimating = true
    }
    var targetOffsetX by remember { mutableFloatStateOf(0f) }
    var targetOffsetY by remember { mutableFloatStateOf(0f) }

    val transition = updateTransition(targetState = isAnimating, label = "captureAnimation")
    val tween = tween<Float>(durationMillis = ANIMATION_DURATION)
    val offsetX by transition.animateFloat({ tween }, "offsetX") { if (it) targetOffsetX else 0f }
    val offsetY by transition.animateFloat({ tween }, "offsetY") { if (it) targetOffsetY else 0f }
    val scale by transition.animateFloat({ tween }, "scale") { if (it) 0.3f else 1f }

    val density = LocalDensity.current
    Box (contentAlignment = Alignment.BottomStart,
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val centerX = bounds.left + bounds.width / 2
                val centerY = bounds.top + bounds.height / 2
                with(density) {
                    targetOffsetX = thumbnailCoords.value.x - centerX
                    targetOffsetY = thumbnailCoords.value.y - centerY
                }
            }
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                    scaleX = scale
                    scaleY = scale
                }
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
                    color = color,
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(color = color, shape = CircleShape)
        )
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    cameraPreview: @Composable () -> Unit,
    cameraUiState: CameraUiState,
    modifier: Modifier,
) {
    val captureState = cameraUiState.captureState
    var width = LocalConfiguration.current.screenWidthDp
    var height = width * 4 / 3
    if (cameraUiState.isLandscape) {
        height = LocalConfiguration.current.screenHeightDp
        width = height * 4 / 3
    }

    var showShutter by remember { mutableStateOf(false) }
    LaunchedEffect(captureState.frozenImage) {
        if (captureState.frozenImage != null) {
            showShutter = true
            delay(200)
            showShutter = false
        }
    }

    Box(
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
    ) {
        cameraPreview()
        AnalysisOverlay(cameraUiState.liveAnalysisState, cameraUiState.isDebugMode)
        captureState.frozenImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
            )

        }
        if (showShutter) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }
        if (cameraUiState.showDetectionError) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.error_no_document),
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
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
private fun Bar(
    pageCount: Int,
    onPageCountClick: () -> Unit,
    onFinalizePressed: () -> Unit,
) {
    Text(
        text = pageCountText(pageCount),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clickable(onClick = onPageCountClick)
    )
    MainActionButton(
        onClick = onFinalizePressed,
        enabled = pageCount > 0,
        text = stringResource(R.string.document),
        icon = Icons.AutoMirrored.Filled.Article,
    )
}

@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    ScreenPreview(CaptureState.Idle)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CameraScreenPreviewWithProcessedImage() {
    ScreenPreview(CaptureState.CapturePreview(
        debugImage("uncropped/img01.jpg"),
        debugImage("gallica.bnf.fr-bpt6k5530456s-1.jpg")))
}

@Preview(showBackground = true, widthDp = 640, heightDp = 320)
@Composable
fun CameraScreenPreviewInLandscapeMode() {
    ScreenPreview(CaptureState.Idle, rotationDegrees = 90f)
}

@Composable
private fun ScreenPreview(captureState: CaptureState, rotationDegrees: Float = 0f) {
    val context = LocalContext.current
    MyScanTheme {
        val thumbnailCoords = remember { mutableStateOf(Offset.Zero) }
        CameraScreenScaffold(
            cameraPreview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Image(
                        debugImage("uncropped/img01.jpg").asImageBitmap(),
                        modifier=Modifier.rotate(rotationDegrees),
                        contentDescription = null
                    )
                }
            },
            pageListState =
                CommonPageListState(
                    document = DocumentUiModel(
                        pageIds = listOf(1, 2, 2, 2).map { "gallica.bnf.fr-bpt6k5530456s-$it.jpg" },
                        imageLoader = { id ->
                            context.assets.open(id).use { input ->
                                BitmapFactory.decodeStream(input)
                            }
                        }),
                    onPageClick = {},
                    listState = LazyListState(),
                ),
            cameraUiState = CameraUiState(pageCount = 4, LiveAnalysisState(), captureState,
                false, rotationDegrees > 0, false),
            onCapture = {},
            onFinalizePressed = {},
            onDebugModeSwitched = {},
            thumbnailCoords = thumbnailCoords,
            toAboutScreen = {}
        )
    }
}

@Composable
private fun debugImage(imgName: String): Bitmap {
    val context = LocalContext.current
    return context.assets.open(imgName).use { input ->
        BitmapFactory.decodeStream(input)
    }
}
