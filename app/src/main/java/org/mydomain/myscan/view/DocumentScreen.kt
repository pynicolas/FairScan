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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import org.mydomain.myscan.Navigation
import org.mydomain.myscan.PdfGenerationActions
import org.mydomain.myscan.R
import org.mydomain.myscan.ui.PdfGenerationUiState
import org.mydomain.myscan.ui.theme.MyScanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    document: DocumentUiModel,
    initialPage: Int,
    navigation: Navigation,
    pdfActions: PdfGenerationActions,
    onStartNew: () -> Unit,
    onDeleteImage: (String) -> Unit,
) {
    // TODO Check how often images are loaded
    val showNewDocDialog = rememberSaveable { mutableStateOf(false) }
    val showPdfDialog = rememberSaveable { mutableStateOf(false) }
    val currentPageIndex = rememberSaveable { mutableIntStateOf(initialPage) }
    if (currentPageIndex.intValue >= document.pageCount()) {
        currentPageIndex.intValue = document.pageCount() - 1
    }
    if (currentPageIndex.intValue < 0) {
        navigation.toCameraScreen()
        return
    }
    BackHandler { navigation.back() }

    val listState = rememberLazyListState()
    LaunchedEffect(currentPageIndex.intValue) {
        listState.animateScrollToItem(currentPageIndex.intValue)
    }

    MyScaffold(
        toAboutScreen = navigation.toAboutScreen,
        pageListState = CommonPageListState(
            document,
            onPageClick = { index -> currentPageIndex.intValue = index },
            currentPageIndex = currentPageIndex.intValue,
            listState = listState,
        ),
        onBack = navigation.back,
        bottomBar = {
            BottomBar(showPdfDialog, showNewDocDialog)
        },
        pageListButton = {
            SecondaryActionButton(
                icon = Icons.Default.Add,
                onClick = navigation.toCameraScreen,
                contentDescription = stringResource(R.string.add_page),
            )
        },
    ) { modifier ->
        DocumentPreview(document, currentPageIndex, onDeleteImage, modifier)
        if (showNewDocDialog.value) {
            NewDocumentDialog(onConfirm = onStartNew, showNewDocDialog, stringResource(R.string.close_document))
        }
        if (showPdfDialog.value) {
            PdfGenerationBottomSheetWrapper(
                onDismiss = { showPdfDialog.value = false },
                pdfActions = pdfActions,
            )
        }
    }
}

@Composable
private fun DocumentPreview(
    document: DocumentUiModel,
    currentPageIndex: MutableIntState,
    onDeleteImage: (String) -> Unit,
    modifier: Modifier,
) {
    val imageId = document.pageId(currentPageIndex.intValue)
    Column (
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box (
            modifier = Modifier.fillMaxSize()
        ) {
            val bitmap = document.load(currentPageIndex.intValue)
            if (bitmap != null) {
                val imageBitmap = bitmap.asImageBitmap()
                val zoomState = rememberZoomState(
                    contentSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                )

                LaunchedEffect(imageId) {
                    zoomState.reset()
                }
                Box(modifier = Modifier
                    .fillMaxSize(0.92f)
                    .align(Alignment.Center)) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.Center)
                            .zoomable(zoomState)
                    )
                }
            }
            SecondaryActionButton(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete_page),
                onClick = { onDeleteImage(imageId) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
            Text("${currentPageIndex.intValue + 1} / ${document.pageCount()}",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(all = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun BottomBar(
    showPdfDialog: MutableState<Boolean>,
    showNewDocDialog: MutableState<Boolean>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        MainActionButton(
            onClick = { showPdfDialog.value = true },
            icon = Icons.Default.PictureAsPdf,
            text = stringResource(R.string.export_pdf),
        )
        Spacer(modifier = Modifier.width(8.dp))
        SecondaryActionButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.restart),
            onClick = { showNewDocDialog.value = true },
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
fun NewDocumentDialog(onConfirm: () -> Unit, showDialog: MutableState<Boolean>, title: String) {
    AlertDialog(
        title = { Text(title) },
        text = { Text(stringResource(R.string.new_document_warning)) },
        confirmButton = {
            TextButton (onClick = {
                showDialog.value = false
                onConfirm()
            }) {
                Text(stringResource(R.string.yes), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { showDialog.value = false }) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
            }
        },
        onDismissRequest = { showDialog.value = false },
    )
}

@Composable
@Preview
fun DocumentScreenPreview() {
    MyScanTheme {
        DocumentScreen(
            fakeDocument(
                listOf(1, 2, 2, 2).map { "gallica.bnf.fr-bpt6k5530456s-$it.jpg" },
                LocalContext.current),
            initialPage = 1,
            navigation = dummyNavigation(),
            pdfActions = PdfGenerationActions(
                {}, {}, {},
                MutableStateFlow(PdfGenerationUiState()),
                {}, {}, {}),
            onStartNew = {},
            onDeleteImage = { _ -> {} }
        )
    }
}

@Preview(showBackground = true, widthDp = 640, heightDp = 320)
@Composable
fun DocumentScreenPreviewInLandscapeMode() {
    DocumentScreenPreview()
}

