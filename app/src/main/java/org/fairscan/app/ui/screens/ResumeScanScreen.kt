/*
 * Copyright 2025-2026 The FairScan authors
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

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import org.fairscan.app.R
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.components.isLandscape
import org.fairscan.app.ui.components.pageCountText
import org.fairscan.app.ui.fakeDocument
import org.fairscan.app.ui.fakeImage
import org.fairscan.app.ui.screens.document.CurrentPageUiState
import org.fairscan.app.ui.screens.document.DocumentUiState
import org.fairscan.app.ui.theme.FairScanTheme
import org.fairscan.imageprocessing.ColorMode


@Composable
fun ResumeScanScreen(
    currentDocument: DocumentUiState,
    onResumeScan: () -> Unit,
    onStartNewScan: () -> Unit,
) {
    val pageCount = currentDocument.document.pageCount()
    val firstPageThumbnail = currentDocument.currentPage?.bitmap

    val resumeScanModifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onResumeScan)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        .padding(horizontal = 16.dp, vertical = 24.dp)

    Scaffold { innerPadding ->
        if (!isLandscape(LocalConfiguration.current)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = resumeScanModifier.weight(2f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FirstPageThumbnail(firstPageThumbnail, Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(20.dp))
                    ResumeScanActions(pageCount, onResumeScan)
                }
                HorizontalDivider()
                NewScanArea(onStartNewScan, Modifier.weight(1f))
            }
        } else {
            Row {
                Row(
                    modifier = resumeScanModifier.weight(1.8f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FirstPageThumbnail(firstPageThumbnail, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        ResumeScanActions(pageCount, onResumeScan)
                    }
                }
                VerticalDivider()
                NewScanArea(onStartNewScan, Modifier.weight(1f))
            }
        }
    }
}
@Composable
private fun FirstPageThumbnail(firstPageThumbnail: Bitmap?, modifier: Modifier) {
    firstPageThumbnail?.let { bmp ->
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun ResumeScanActions(pageCount: Int, onResumeScan: () -> Unit) {
    Row {
        Text(stringResource(R.string.scan_current))
        Text(" • ")
        Text(pageCountText(pageCount))
    }
    BigButton(onClick = onResumeScan, text = stringResource(R.string.scan_resume))
}

@Composable
private fun NewScanArea(onStartNewScan: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onStartNewScan)
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.scan_discard_current),
            textAlign = TextAlign.Center,
        )
        BigButton(onClick = onStartNewScan, text = stringResource(R.string.scan_start_new))
    }
}

@Composable
fun BigButton(onClick: () -> Unit, text: String) {
    Button(onClick = onClick, modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Preview(showBackground = true, widthDp = 640, heightDp = 320)
@Composable
fun ResumeScanScreenPreview() {
    val image = fakeImage("gallica.bnf.fr-bpt6k5530456s-1", LocalContext.current).toBitmap()
    val document = fakeDocument(
        listOf(1, 2).map { "gallica.bnf.fr-bpt6k5530456s-$it" }.toImmutableList(),
        LocalContext.current
    )
    val key = PageViewKey("123", Rotation.R0, null, 0)
    FairScanTheme {
        ResumeScanScreen(
            DocumentUiState(1, CurrentPageUiState(key,image, ColorMode.COLOR, true), document),
             {}, {}
        )
    }
}
