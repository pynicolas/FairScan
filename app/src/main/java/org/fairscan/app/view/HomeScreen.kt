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
package org.fairscan.app.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import org.fairscan.app.CameraPermissionState
import org.fairscan.app.Navigation
import org.fairscan.app.R
import org.fairscan.app.rememberCameraPermissionState
import org.fairscan.app.ui.RecentDocumentUiState
import org.fairscan.app.ui.theme.FairScanTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    cameraPermission: CameraPermissionState,
    currentDocument: DocumentUiModel,
    navigation: Navigation,
    onClearScan: () -> Unit,
    recentDocuments: List<RecentDocumentUiState>,
    onOpenPdf: (File) -> Unit,
) {
    Scaffold (
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    AboutScreenNavButton(onClick = navigation.toAboutScreen)
                }
            )
        },
    ) { padding ->
        Column (
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.weight(1f))

            if (!cameraPermission.isGranted) {
                CameraPermissionRationale(cameraPermission)
            } else {
                ScanButton(
                    onClick = {
                        onClearScan()
                        navigation.toCameraScreen()
                    },
                    Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(Modifier.weight(1f))

            if (!currentDocument.isEmpty()) {
                OngoingScanBanner(
                    currentDocument,
                    onResumeScan = navigation.toDocumentScreen,
                    onClearScan = onClearScan,
                )
            } else if (recentDocuments.isNotEmpty()) {
                RecentDocumentList(recentDocuments, onOpenPdf)
            }
        }
    }
}

@Composable
private fun CameraPermissionRationale(cameraPermission: CameraPermissionState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.camera_permission_rationale),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { cameraPermission.request() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}

@Composable
fun ScanButton(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(32.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.scan_button),
            style = MaterialTheme.typography.titleLarge
        )

    }
}

@Composable
fun OngoingScanBanner(
    currentDocument: DocumentUiModel,
    onResumeScan: () -> Unit,
    onClearScan: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            currentDocument.load(0)?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.scan_in_progress),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = pageCountText(currentDocument.pageCount()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }

            IconButton(
                onClick = onClearScan,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.discard_scan),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onResumeScan) {
                Text(stringResource(R.string.resume))
            }
        }
    }
}

@Composable
private fun RecentDocumentList(
    recentDocuments: List<RecentDocumentUiState>,
    onOpenPdf: (File) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.last_saved_pdf_files),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
    )
    Column {
        recentDocuments.forEach { doc ->
            ListItem(
                headlineContent = {
                    Text(
                        doc.file.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                supportingContent = {
                    Text(
                        text = pageCountText(doc.pageCount) + " • " +
                                formatDate(doc.saveTimestamp, LocalContext.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { onOpenPdf(doc.file) }
            )
        }
    }
}

@Preview
@Composable
fun HomeScreenPreviewOnFirstLaunch() {
    FairScanTheme {
        HomeScreen(
            cameraPermission = rememberCameraPermissionState(),
            currentDocument = fakeDocument(),
            navigation = dummyNavigation(),
            onClearScan = {},
            recentDocuments = listOf(),
            onOpenPdf = {},
        )
    }
}

@Preview
@Composable
fun HomeScreenPreviewWithCurrentDocument() {
    FairScanTheme {
        HomeScreen(
            cameraPermission = rememberCameraPermissionState(),
            currentDocument = fakeDocument(
                persistentListOf("gallica.bnf.fr-bpt6k5530456s-1.jpg"),
                LocalContext.current),
            navigation = dummyNavigation(),
            onClearScan = {},
            recentDocuments = listOf(),
            onOpenPdf = {},
        )
    }
}

@Preview
@Composable
fun HomeScreenPreviewWithLastSavedFiles() {
    FairScanTheme {
        HomeScreen(
            cameraPermission = rememberCameraPermissionState(),
            currentDocument = fakeDocument(),
            navigation = dummyNavigation(),
            onClearScan = {},
            recentDocuments = listOf(
                RecentDocumentUiState(File("/path/my_file.pdf"), 1755971180000, 3),
                RecentDocumentUiState(File("/path/scan2.pdf"), 1755000500000, 1)
            ),
            onOpenPdf = {},
        )
    }
}
