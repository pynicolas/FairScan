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
package org.fairscan.app.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.zoomable
import org.fairscan.app.R
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.CommonPageListState
import org.fairscan.app.ui.components.ConfirmationDialog
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.MyScaffold
import org.fairscan.app.ui.components.SecondaryActionButton
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.fakeDocument
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.theme.FairScanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    document: DocumentUiModel,
    initialPage: Int,
    navigation: Navigation,
    onExportClick: () -> Unit,
    onDeleteImage: (String) -> Unit,
    onRotateImage: (String, Boolean) -> Unit,
    onPageReorder: (String, Int) -> Unit,
) {
    // TODO Check how often images are loaded
    val showDeletePageDialog = rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(initialPage) {
        listState.scrollToItem(initialPage)
    }

    MyScaffold(
        navigation = navigation,
        pageListState = CommonPageListState(
            document,
            onPageClick = { index -> currentPageIndex.intValue = index },
            onPageReorder = onPageReorder,
            currentPageIndex = currentPageIndex.intValue,
            listState = listState,
            showPageNumbers = true,
        ),
        onBack = navigation.back,
        bottomBar = {
            BottomBar(onExportClick, navigation.toCameraScreen)
        },
    ) { modifier ->
        DocumentPreview(
            document,
            currentPageIndex,
            { showDeletePageDialog.value = true },
            onRotateImage,
            modifier
        )
        if (showDeletePageDialog.value) {
            ConfirmationDialog(
                title = stringResource(R.string.delete_page),
                message = stringResource(R.string.delete_page_warning),
                showDialog = showDeletePageDialog
            ) { onDeleteImage(document.pageId(currentPageIndex.intValue)) }
        }
    }
}

@Composable
private fun DocumentPreview(
    document: DocumentUiModel,
    currentPageIndex: MutableIntState,
    onDeleteImage: (String) -> Unit,
    onRotateImage: (String, Boolean) -> Unit,
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
                val zoomState = remember(imageId) {
                    ZoomState(
                        contentSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                    )
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
            RotationButtons(imageId, onRotateImage, Modifier.align(Alignment.BottomCenter))
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
fun RotationButtons(
    imageId: String,
    onRotateImage: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(4.dp)) {
        SecondaryActionButton(
            icon = Icons.AutoMirrored.Default.RotateLeft,
            contentDescription = "Rotate left",
            onClick = { onRotateImage(imageId, false) }
        )
        Spacer(Modifier.width(8.dp))
        SecondaryActionButton(
            icon = Icons.AutoMirrored.Default.RotateRight,
            contentDescription = "Rotate right",
            onClick = { onRotateImage(imageId, true) }
        )
    }
}

@Composable
private fun BottomBar(
    onExportClick: () -> Unit,
    onAddPageClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(
            onClick = onAddPageClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.add_page),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        MainActionButton(
            onClick = onExportClick,
            icon = Icons.Default.Done,
            text = stringResource(R.string.export),
        )
    }
}

@Composable
@Preview
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Landscape", showBackground = true, widthDp = 640, heightDp = 320)
fun DocumentScreenPreview() {
    FairScanTheme {
        DocumentScreen(
            fakeDocument(
                listOf(1, 2).map { "gallica.bnf.fr-bpt6k5530456s-$it" }.toImmutableList(),
                LocalContext.current
            ),
            initialPage = 1,
            navigation = dummyNavigation(),
            onExportClick = {},
            onDeleteImage = { _ -> },
            onRotateImage = { _,_ -> },
            onPageReorder = { _,_ -> },
        )
    }
}
