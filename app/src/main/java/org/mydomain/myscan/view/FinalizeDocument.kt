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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalizeDocumentScreen(
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
    ) {
        Text(
            "Pages",
            modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            style = MaterialTheme.typography.titleMedium
        )
        FlowRow(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            pageIds.forEachIndexed { index, id ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // TODO Display small images rather than big ones
                    // TODO Make it possible to zoom on an image
                    Box {
                        Image(
                            bitmap = imageLoader(id).asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier
                                .size(160.dp)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.DarkGray)
                        )

                        IconButton(
                            onClick = { onDeleteImage(id) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun DocumentScreenPreview() {
    val context = LocalContext.current
    FinalizeDocumentScreen(
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
