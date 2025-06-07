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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    pageIds: List<String>,
    imageLoader: (String) -> Bitmap,
    onBackPressed: () -> Unit,
    onSavePressed: () -> Unit,
    onSharePressed: () -> Unit,
    onDeleteImage: (String) -> Unit,
) {
    Scaffold (
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = { Text("Finalize document") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = onSharePressed) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                    Button(onClick = onSavePressed) {
                        Text("Save")
                    }
                }
            }
        }
    ) { padding -> DocumentPreview(pageIds, padding, imageLoader, onDeleteImage) }
}

@Composable
private fun DocumentPreview(
    pageIds: List<String>,
    padding: PaddingValues,
    imageLoader: (String) -> Bitmap,
    onDeleteImage: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(PaddingValues(top = padding.calculateTopPadding()))
    ) {
        Text(
            "${pageIds.size} pages",
            modifier = Modifier.padding(start = 16.dp, top = 16.dp).align(Alignment.TopStart),
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn (
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pageIds) { id ->
                // TODO Display small images rather than big ones
                // TODO Make it possible to zoom on an image
                val bitmap = imageLoader(id).asImageBitmap()
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .padding(4.dp)
                )
            }
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
        onBackPressed = {},
        onSavePressed = {},
        onSharePressed = {},
        onDeleteImage = { _ -> {} }
    )
}
