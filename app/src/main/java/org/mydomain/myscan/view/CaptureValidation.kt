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
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mydomain.myscan.MainViewModel
import org.mydomain.myscan.ui.theme.MyScanTheme

@Composable
fun CaptureValidationScreen(
    imageProxy: ImageProxy,
    viewModel: MainViewModel,
    onConfirm: (Bitmap) -> Unit,
    onReject: () -> Unit,
    onError: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hasBeenLaunched by remember { mutableStateOf(false) }

    // Start processing only once
    if (!hasBeenLaunched) {
        hasBeenLaunched = true
        viewModel.processCapturedImageThen(imageProxy) { result ->
            resultBitmap = result
            isLoading = false
        }
    }

    CaptureValidationContent(isLoading, resultBitmap, onError, onReject, onConfirm)
}

@Composable
private fun CaptureValidationContent(
    isLoading: Boolean,
    resultBitmap: Bitmap?,
    onError: () -> Unit,
    onReject: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    Scaffold { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(innerPadding)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                resultBitmap == null -> {
                    Text(
                        "Document not detected",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Button(
                        onClick = onError,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text("OK")
                    }
                }

                else -> {
                    Image(
                        bitmap = resultBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(onClick = onReject, modifier = Modifier.weight(1f)) {
                            Text("Reject")
                        }
                        Button(
                            onClick = {onConfirm(resultBitmap)},
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CaptureValidationPreview() {
    val context = LocalContext.current
    val bitmap = context.assets.open("gallica.bnf.fr-bpt6k5530456s-1.jpg")
        .use { input -> BitmapFactory.decodeStream(input) }
    MyScanTheme {
        CaptureValidationContent(
            isLoading = false,
            resultBitmap = bitmap,
            onError = {},
            onConfirm = {},
            onReject = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CaptureValidationWithoutImagePreview() {
    MyScanTheme {
        CaptureValidationContent(
            isLoading = false,
            resultBitmap = null,
            onError = {},
            onConfirm = {},
            onReject = {}
        )
    }
}
