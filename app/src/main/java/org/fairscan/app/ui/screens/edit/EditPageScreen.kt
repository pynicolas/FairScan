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

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
import org.fairscan.app.ui.components.SecondaryActionButton
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.theme.FairScanTheme
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import java.io.File

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

    val baseRotation = remember { mutableStateOf(Rotation.R0) }

    LaunchedEffect(pageId) {
        val metadata = imageRepository.getPageMetadata(pageId)
        val rotation = metadata?.baseRotation ?: Rotation.R0
        baseRotation.value = rotation

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

    Scaffold { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
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
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                canUndo = state.history.canUndo,
                canRedo = state.history.canRedo,
                onUndo = { state.undo() },
                onRedo = { state.redo() },
                onConfirm = {
                    state.editableQuad?.let { quad ->
                        if (state.hasUnsavedChanges()) {
                            // Reverse the rotation to get back to original source image coordinates
                            val rotateIterations = (4 - baseRotation.value.degrees / 90) % 4
                            val originalQuad = quad.rotate90(rotateIterations, ImageSize(1, 1))
                            // Cycle the quad corners so that the perspective warp in
                            // extractDocument produces output already rotated by
                            // baseRotation, compensating for the fact that updatePageQuad
                            // only applies manualRotationDegrees.
                            val cycledQuad = cycleQuadCorners(
                                originalQuad, baseRotation.value.degrees / 90
                            )
                            onUpdatePageQuad(pageId, cycledQuad)
                            state.setInitialQuad(quad)
                        }
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
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SecondaryActionButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            onClick = onUndo,
            enabled = canUndo
        )

        SecondaryActionButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            onClick = onRedo,
            enabled = canRedo
        )

        SecondaryActionButton(
            icon = Icons.Filled.Check,
            contentDescription = "Confirm",
            onClick = onConfirm
        )
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

                    val cornerIndex = quadHandler.findTouchedCorner(
                        startPos, quad, containerSize, displaySize
                    )

                    if (cornerIndex >= 0) {
                        state.startCornerDrag(cornerIndex)
                    } else {
                        val edgeIndex = quadHandler.findTouchedEdge(
                            startPos, quad, containerSize, displaySize
                        )
                        if (edgeIndex >= 0) {
                            state.startEdgeDrag(edgeIndex)
                        }
                    }
                },
                onDragEnd = { state.endDrag() },
                onDragCancel = { state.endDrag() },
                onDrag = { change, dragAmount ->
                    change.consume()
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
    )
}

@Composable
private fun DragMagnifyingGlass(state: EditPageScreenState) {
    if (!state.isDragging() || state.dragPosition == null || state.containerSize == null) return

    val bmp = state.bitmap ?: return
    val containerSize = state.containerSize!!
    val displaySize = QuadCoordinateUtils.calculateDisplaySize(
        bmp.width, bmp.height, containerSize
    )
    val quad = state.editableQuad

    val focusPosition = if (quad != null) {
        when {
            state.draggedCornerIndex >= 0 -> {
                val corner = when (state.draggedCornerIndex) {
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
            state.draggedEdgeIndex >= 0 -> {
                val (p1, p2) = when (state.draggedEdgeIndex) {
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

    if (focusPosition != null) {
        MagnifyingGlass(
            bitmap = bmp,
            fingerPosition = state.dragPosition!!,
            focusPosition = focusPosition,
            containerSize = containerSize,
            displaySize = displaySize,
        )
    }
}

@Composable
@Preview
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Landscape", showBackground = true, widthDp = 640, heightDp = 320)
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

/**
 * Cycles the corner labels of a [Quad] so that a perspective warp using the
 * returned quad produces output that is already rotated by [iterations] × 90°
 * clockwise, without moving any source-image point.
 *
 * This compensates for [ImageRepository.updatePageQuad] passing only
 * `manualRotationDegrees` (not `baseRotationDegrees`) to `extractDocument`.
 */
internal fun cycleQuadCorners(quad: Quad, iterations: Int): Quad {
    return when ((iterations % 4 + 4) % 4) {
        1 -> Quad(quad.bottomLeft, quad.topLeft, quad.topRight, quad.bottomRight)
        2 -> Quad(quad.bottomRight, quad.bottomLeft, quad.topLeft, quad.topRight)
        3 -> Quad(quad.topRight, quad.bottomRight, quad.bottomLeft, quad.topLeft)
        else -> quad
    }
}
