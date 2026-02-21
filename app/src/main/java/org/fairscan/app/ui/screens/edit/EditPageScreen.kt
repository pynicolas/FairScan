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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.AppOverflowMenu
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.theme.FairScanTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPageScreen(
    pageId: String,
    imageRepository: ImageRepository,
    navigation: Navigation,
) {
    BackHandler { navigation.back() }

    val state = remember { EditPageScreenState() }
    val quadHandler = remember { QuadEditingHandler() }

    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        val dummyImage = LocalContext.current.assets.open("gallica.bnf.fr-bpt6k5530456s-1.jpg").use { input ->
            BitmapFactory.decodeStream(input)
        }
        state.bitmap = dummyImage
    }

    LaunchedEffect(pageId) {
        val metadata = imageRepository.getPageMetadata(pageId)
        val rotation = metadata?.baseRotation ?: Rotation.R0
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
        state.editableQuad = imageRepository.getPageMetadata(pageId)?.normalizedQuad
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

                    if (state.editableQuad != null && state.containerSize != null) {
                        val containerSize = state.containerSize!!
                        val displaySize = QuadCoordinateUtils.calculateDisplaySize(
                            bmp.width, bmp.height, containerSize
                        )

                        QuadOverlay(
                            quad = state.editableQuad!!,
                            containerSize = containerSize,
                            displaySize = displaySize,
                            modifier = Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { startPos ->
                                        val quad = state.editableQuad ?: return@detectDragGestures

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
                }
            }

            BackButton(
                navigation.back,
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
        }
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
        }

        // Use a temporary directory for the repository in preview.
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val dummyImageRepo = ImageRepository(tempDir, dummyTransformations, 128)

        EditPageScreen(
            pageId = "preview-page-id",
            imageRepository = dummyImageRepo,
            navigation = dummyNavigation(),
        )
    }
}
