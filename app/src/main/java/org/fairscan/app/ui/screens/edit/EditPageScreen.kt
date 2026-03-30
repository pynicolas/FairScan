/*
 * Copyright 2026 Philipp Hasper
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
package org.fairscan.app.ui.screens.edit

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.fairscan.app.R
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.AppOverflowMenu
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.components.ConfirmationDialog
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.SecondaryActionButton
import org.fairscan.app.ui.components.isLandscape
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.theme.FairScanTheme
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import java.io.File

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPageScreen(
    pageId: String,
    imageRepository: ImageRepository,
    navigation: Navigation,
    onUpdatePageQuad: (String, Quad) -> Unit,
) {
    val showDiscardChangesDialog = rememberSaveable { mutableStateOf(false) }
    val state = remember { EditPageScreenState() }
    val quadHandler = remember { QuadEditingHandler() }

    val handleBack = {
        if (state.hasUnsavedChanges()) {
            showDiscardChangesDialog.value = true
        } else {
            navigation.back()
        }
    }

    BackHandler { handleBack() }

    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        val dummyImage = LocalContext.current.assets.open("gallica.bnf.fr-bpt6k5530456s-1.jpg").use { input ->
            BitmapFactory.decodeStream(input)
        }
        state.bitmap = dummyImage
        state.setInitialQuad(Quad(Point(.1, .1), Point(.9, .1), Point(.9, .9), Point(.1, .9)))
    }

    val totalRotation = remember { mutableStateOf(Rotation.R0) }

    LaunchedEffect(pageId) {
        val metadata = imageRepository.getPageMetadata(pageId)
        val baseRotation = metadata?.baseRotation ?: Rotation.R0
        val manualRotation = imageRepository.getManualRotation(pageId)
        val rotation = baseRotation.add(manualRotation)
        totalRotation.value = rotation

        val bitmap = withContext(Dispatchers.IO) {
            val sourceJpegBytes = imageRepository.sourceJpegBytes(pageId)
            if (sourceJpegBytes != null) {
                val original = BitmapFactory.decodeByteArray(sourceJpegBytes, 0, sourceJpegBytes.size)
                if (original != null && rotation != Rotation.R0) {
                    // Adjust the displayed bitmap's rotation to what is in the metadata
                    val matrix = Matrix().apply { postRotate(rotation.degrees.toFloat()) }
                    val rotated = android.graphics.Bitmap.createBitmap(
                        original, 0, 0, original.width, original.height, matrix, true
                    )
                    if (rotated !== original) {
                        original.recycle()
                    }
                    rotated
                } else {
                    original
                }
            } else null
        }
        state.bitmap = bitmap  // assigned on the main thread after withContext returns
        if (metadata?.normalizedQuad != null) {
            // Rotate the quad to match the rotated bitmap display
            val rotatedQuad = metadata.normalizedQuad.rotate90(
                rotation.degrees / 90,
                ImageSize(1, 1)
            )
            state.setInitialQuad(rotatedQuad)
        }
    }

    val isLandscape = isLandscape(LocalConfiguration.current)

    Scaffold { _ ->
        Box(modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            state.bitmap?.let { bmp ->
                val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Image to edit",
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                state.containerSize = coordinates.size
                            },
                        contentScale = ContentScale.Fit,
                    )

                    DragQuadOverlay(state, quadHandler, bmp)
                }
            }

            BackButton(
                onClick = handleBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            )
            AppOverflowMenu(
                navigation,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            )

            DragMagnifyingGlass(state)

            ActionButtons(
                modifier = Modifier
                    .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                isLandscape = isLandscape,
                canUndo = state.history.canUndo,
                canRedo = state.history.canRedo,
                onUndo = { state.undo() },
                onRedo = { state.redo() },
                onConfirm = {
                    val quad = state.editableQuad
                    if (quad != null) {
                        // Reverse the total rotation to get back to original source image coordinates
                        val rotateIterations = (4 - totalRotation.value.degrees / 90) % 4
                        val originalQuad = quad.rotate90(rotateIterations, ImageSize(1, 1))
                        onUpdatePageQuad(pageId, originalQuad) {
                            navigation.back()
                        }
                        state.setInitialQuad(quad)
                    } else {
                        navigation.back()
                    }
                    navigation.back()
                }
            )
        }
    }

    if (showDiscardChangesDialog.value) {
        ConfirmationDialog(
            title = stringResource(R.string.discard_changes),
            message = stringResource(R.string.discard_changes_warning),
            showDialog = showDiscardChangesDialog
        ) {
            state.revertToInitial()
            navigation.back()
        }
    }
}

@Composable
private fun ActionButtons(
    modifier: Modifier,
    isLandscape: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onConfirm: () -> Unit
) {
    val undo: @Composable () -> Unit = {
        SecondaryActionButton(Icons.AutoMirrored.Filled.Undo,
            stringResource(R.string.undo),
            onUndo,
            enabled = canUndo)
    }
    val redo: @Composable () -> Unit = {
        SecondaryActionButton(Icons.AutoMirrored.Filled.Redo,
            stringResource(R.string.redo),
            onRedo,
            enabled = canRedo)
    }
    val confirm: @Composable () -> Unit = {
        MainActionButton(onConfirm,
            stringResource(R.string.confirm),
            Icons.Filled.Check,
            iconDescription = stringResource(R.string.confirm))
    }

    if (isLandscape) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { undo(); redo() }
            confirm()
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            undo(); redo(); confirm()
        }
    }
}

@Composable
private fun DragQuadOverlay(
    state: EditPageScreenState,
    quadHandler: QuadEditingHandler,
    bmp: android.graphics.Bitmap
) {
    if (state.editableQuad == null || state.containerSize == null) return

    val containerSize = state.containerSize!!
    val displaySize = QuadCoordinateUtils.calculateDisplaySize(bmp.width, bmp.height, containerSize)

    QuadOverlay(
        quad = state.editableQuad!!,
        containerSize = containerSize,
        displaySize = displaySize,
        modifier = Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startPos ->
                        val quad = state.editableQuad ?: return@detectDragGestures
                        state.dragPosition = startPos

                        // Prefer the index stored at raw touch-down (exact touch position,
                        // before slop). Fall back to re-detecting at the slop position only
                        // when the raw-touch handler missed the down event.
                        val cornerIndex = if (state.touchDownCornerIndex >= 0) {
                            state.touchDownCornerIndex
                        } else {
                            quadHandler.findTouchedCorner(startPos, quad, containerSize, displaySize)
                        }

                        if (cornerIndex >= 0) {
                            state.startCornerDrag(cornerIndex)
                        } else {
                            val edgeIndex = if (state.touchDownEdgeIndex >= 0) {
                                state.touchDownEdgeIndex
                            } else {
                                quadHandler.findTouchedEdge(startPos, quad, containerSize, displaySize)
                            }
                            if (edgeIndex >= 0) {
                                state.startEdgeDrag(edgeIndex)
                            }
                        }
                    },
                    onDragEnd = { state.endDrag(); state.onTouchUp() },
                    onDragCancel = { state.endDrag(); state.onTouchUp() },
                    onDrag = { change, dragAmount ->
                        // change.consume() is intentionally omitted: detectDragGestures
                        // already calls it.consume() internally after this callback returns.
                        state.dragPosition = change.position
                        val quad = state.editableQuad ?: return@detectDragGestures
                        val normalizedDelta = QuadCoordinateUtils.screenDeltaToNormalized(
                            dragAmount, displaySize
                        )

                        when {
                            state.draggedCornerIndex >= 0 -> {
                                state.updateQuad(
                                    quadHandler.updateQuadCorner(
                                        quad, state.draggedCornerIndex, normalizedDelta
                                    )
                                )
                            }
                            state.draggedEdgeIndex >= 0 -> {
                                state.updateQuad(
                                    quadHandler.updateQuadEdge(
                                        quad, state.draggedEdgeIndex, normalizedDelta
                                    )
                                )
                            }
                        }
                    }
                )
            }
            // Second pointer-input: fires immediately on press (before touch slop)
            // so the loupe appears as soon as the finger touches a handle.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val quad = state.editableQuad
                    if (quad != null) {
                        val cIdx = quadHandler.findTouchedCorner(down.position, quad, containerSize, displaySize)
                        val eIdx = if (cIdx < 0) quadHandler.findTouchedEdge(down.position, quad, containerSize, displaySize) else -1
                        if (cIdx >= 0 || eIdx >= 0) {
                            state.onTouchDown(down.position, cIdx, eIdx)
                        }
                    }
                    // For a tap (no drag): waitForUpOrCancellation() sees the UP event and
                    // returns it, so we call onTouchUp() here.
                    // For a drag: detectDragGestures consumes move events, causing
                    // waitForUpOrCancellation() to return null. We do NOT call onTouchUp()
                    // here; onDragEnd / onDragCancel above handle that instead.
                    if (waitForUpOrCancellation() != null) {
                        state.onTouchUp()
                    }
                }
            }
    )
}

@Composable
private fun DragMagnifyingGlass(state: EditPageScreenState) {
    // showLoupe becomes true immediately on touch-down and stays true for
    // one additional second after the finger is lifted.
    val showLoupe = remember { mutableStateOf(false) }
    // Remember the last valid focus position so the loupe keeps rendering
    // correctly during the 1-second fade-out (when dragged indices are reset).
    val lastKnownFocusPosition = remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(state.isTouching) {
        if (state.isTouching) {
            showLoupe.value = true
        } else {
            delay(1_000)
            showLoupe.value = false
        }
    }

    if (!showLoupe.value || state.dragPosition == null || state.containerSize == null) return

    val bmp = state.bitmap ?: return
    val containerSize = state.containerSize!!
    val displaySize = QuadCoordinateUtils.calculateDisplaySize(
        bmp.width, bmp.height, containerSize
    )
    val quad = state.editableQuad

    // Resolve which corner/edge index to focus on.
    // Priority: active drag > pre-drag touch-down > nothing (fade-out phase).
    val activeCornerIndex = state.draggedCornerIndex.takeIf { it >= 0 }
        ?: state.touchDownCornerIndex.takeIf { it >= 0 }
    val activeEdgeIndex = if (activeCornerIndex == null) {
        state.draggedEdgeIndex.takeIf { it >= 0 }
            ?: state.touchDownEdgeIndex.takeIf { it >= 0 }
    } else null

    val focusPosition = if (quad != null) {
        when {
            activeCornerIndex != null -> {
                val corner = when (activeCornerIndex) {
                    0 -> quad.topLeft
                    1 -> quad.topRight
                    2 -> quad.bottomRight
                    3 -> quad.bottomLeft
                    else -> null
                }
                corner?.let {
                    QuadCoordinateUtils.normalizedToScreen(it, containerSize, displaySize)
                }
            }
            activeEdgeIndex != null -> {
                val (p1, p2) = when (activeEdgeIndex) {
                    0 -> quad.topLeft to quad.topRight
                    1 -> quad.topRight to quad.bottomRight
                    2 -> quad.bottomRight to quad.bottomLeft
                    3 -> quad.bottomLeft to quad.topLeft
                    else -> null to null
                }
                if (p1 != null && p2 != null) {
                    val mid = Point(
                        (p1.x + p2.x) / 2.0,
                        (p1.y + p2.y) / 2.0
                    )
                    QuadCoordinateUtils.normalizedToScreen(mid, containerSize, displaySize)
                } else null
            }
            else -> null
        }
    } else null

    // Keep the last known focus position so it's still valid after endDrag() resets the indices.
    if (focusPosition != null) lastKnownFocusPosition.value = focusPosition
    // On the very first touch the drag indices are not set yet and lastKnownFocusPosition
    // has never been populated, so fall back to dragPosition (the finger is on the handle).
    val effectiveFocusPosition = focusPosition ?: lastKnownFocusPosition.value ?: state.dragPosition ?: return

    MagnifyingGlass(
        bitmap = bmp,
        fingerPosition = state.dragPosition!!,
        focusPosition = effectiveFocusPosition,
        containerSize = containerSize,
        displaySize = displaySize,
        quad = state.editableQuad,
    )
}

@Composable
@Preview(showSystemUi = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true)
@Preview(name = "Landscape", showBackground = true, widthDp = 640, heightDp = 320)
@Preview(name = "RTL", locale = "ar", showSystemUi = true)
fun EditPageScreenPreview() {
    FairScanTheme {

        // Minimal no-op ImageTransformations implementation used only for preview.
        val dummyTransformations = object : ImageTransformations {
            override fun rotate(inputFile: File, outputFile: File, rotationDegrees: Int, jpegQuality: Int) = Unit
            override fun resize(inputFile: File, outputFile: File, maxSize: Int) = Unit
            override fun extractDocument(
                inputFile: File,
                outputFile: File,
                normalizedQuad: Quad,
                rotationDegrees: Int,
                isColored: Boolean,
                quality: ExportQuality
            ) = Unit
        }

        // Use a temporary directory for the repository in preview.
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val dummyImageRepo = ImageRepository(tempDir, dummyTransformations, 128)

        EditPageScreen(
            pageId = "preview-page-id",
            imageRepository = dummyImageRepo,
            navigation = dummyNavigation(),
            onUpdatePageQuad = { _, _ -> }
        )
    }
}
