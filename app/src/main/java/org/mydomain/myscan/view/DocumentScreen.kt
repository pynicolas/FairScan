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
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    pageIds: List<String>,
    imageLoader: (String) -> Bitmap,
    toCameraScreen: () -> Unit,
    onSavePressed: () -> Unit,
    onSharePressed: () -> Unit,
    onDeleteImage: (String) -> Unit,
) {
    val currentPageIndex = rememberSaveable { mutableIntStateOf(0) }
    if (currentPageIndex.intValue >= pageIds.size) {
        currentPageIndex.intValue = pageIds.size - 1
    }
    if (currentPageIndex.intValue < 0) {
        toCameraScreen()
        return
    }
    Scaffold (
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = { Text("Finalize document") },
                navigationIcon = {
                    IconButton(onClick = toCameraScreen) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column {
                PageList(pageIds, imageLoader, currentPageIndex, toCameraScreen)
                BottomAppBar(
                    actions = {
                        Button(onClick = onSharePressed) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                            Spacer(Modifier.width(8.dp))
                            Text("Share")
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(onClick = onSavePressed) {
                            Text("Save")
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = toCameraScreen) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            }
        }
    ) { padding -> DocumentPreview(pageIds, imageLoader, currentPageIndex, onDeleteImage, padding) }
}

@Composable
private fun DocumentPreview(
    pageIds: List<String>,
    imageLoader: (String) -> Bitmap,
    currentPageIndex: MutableIntState,
    onDeleteImage: (String) -> Unit,
    padding: PaddingValues,
) {
    val imageId = pageIds[currentPageIndex.intValue]
    Column (
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(padding)
    ) {
        Box (
            modifier = Modifier.fillMaxSize()
        ) {
            // TODO Make it possible to zoom on the image
            Image(
                bitmap = imageLoader(imageId).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.Center)
            )
            IconButton(
                onClick = { onDeleteImage(imageId) },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete page")
            }
            Text("${currentPageIndex.value + 1} / ${pageIds.size}",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(all = 8.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f))
                    .padding(all = 4.dp)
            )
        }
    }
}

@Composable
private fun PageList(
    pageIds: List<String>,
    imageLoader: (String) -> Bitmap,
    currentPageIndex: MutableState<Int>,
    toCameraScreen: () -> Unit
) {
    Box {
        LazyRow(
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed (pageIds) { index, id ->
                // TODO Use small images rather than big ones
                val bitmap = imageLoader(id).asImageBitmap()
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .height(120.dp)
                        .padding(4.dp)
                        .clickable { currentPageIndex.value = index }
                )
            }
        }
        SmallFloatingActionButton(
            onClick = toCameraScreen,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add page")
        }
    }
}

@Composable
@Preview
fun DocumentScreenPreview() {
    val context = LocalContext.current
    DocumentScreen(
        pageIds = listOf(1, 2, 2, 2).map { "gallica.bnf.fr-bpt6k5530456s-$it.jpg" },
        imageLoader = { id ->
            context.assets.open(id).use { input ->
                BitmapFactory.decodeStream(input)
            }
        },
        toCameraScreen = {},
        onSavePressed = {},
        onSharePressed = {},
        onDeleteImage = { _ -> {} }
    )
}
