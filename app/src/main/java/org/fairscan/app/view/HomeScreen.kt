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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fairscan.app.CameraPermissionState
import org.fairscan.app.Navigation
import org.fairscan.app.R
import org.fairscan.app.rememberCameraPermissionState
import org.fairscan.app.ui.RecentDocumentUiState
import org.fairscan.app.ui.theme.MyScanTheme
import java.io.File
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    cameraPermission: CameraPermissionState,
    currentDocument: DocumentUiModel,
    navigation: Navigation,
    onStartNewScan: () -> Unit,
    recentDocuments: List<RecentDocumentUiState>,
    onOpenPdf: (File) -> Unit,
) {
    val showCloseDocDialog = rememberSaveable { mutableStateOf(false) }
    Scaffold (
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    AboutScreenNavButton(onClick = navigation.toAboutScreen)
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(Modifier.weight(1f))
                MainActionButton(
                    onClick = {
                        if (currentDocument.isEmpty()) {
                            onStartNewScan()
                        } else {
                            showCloseDocDialog.value = true
                        }
                    },
                    icon = Icons.Default.PhotoCamera,
                    text = stringResource(R.string.start_a_new_scan),
                    modifier = Modifier
                        .padding(12.dp)
                        .height(48.dp),
                    )
            }
        }
    ) { padding ->
        Column (
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (!cameraPermission.isGranted) {
                CameraPermissionRationale(cameraPermission)
            }

            if (!currentDocument.isEmpty()) {
                SectionTitle(stringResource(R.string.current_document))
                CurrentDocumentCard(currentDocument, navigation)
            }

            if (recentDocuments.isNotEmpty()) {
                SectionTitle(stringResource(R.string.last_saved_documents))
                RecentDocumentList(recentDocuments, onOpenPdf)
            }

            if (showCloseDocDialog.value) {
                NewDocumentDialog(
                    onConfirm = onStartNewScan,
                    showCloseDocDialog,
                    stringResource(R.string.new_document))
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
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { cameraPermission.request() }) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}

@Composable
private fun CurrentDocumentCard(
    currentDocument: DocumentUiModel,
    navigation: Navigation,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            currentDocument.load(0)?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .height(100.dp)
                        .padding(4.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pageCountText(currentDocument.pageCount()))
            }
            MainActionButton(navigation.toDocumentScreen, stringResource(R.string.open))
        }
    }
}

@Composable
private fun RecentDocumentList(
    recentDocuments: List<RecentDocumentUiState>,
    onOpenPdf: (File) -> Unit
) {
    Column {
        val maxListSize = 5
        recentDocuments.subList(0, min(maxListSize, recentDocuments.size)).forEach { doc ->
            ListItem(
                headlineContent = { Text(doc.file.name) },
                supportingContent = {
                    Text(
                        text = pageCountText(doc.pageCount) + " • " +
                                formatDate(doc.saveTimestamp, LocalContext.current)
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                },
                modifier = Modifier.clickable { onOpenPdf(doc.file) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Preview
@Composable
fun HomeScreenPreviewOnFirstLaunch() {
    MyScanTheme {
        HomeScreen(
            cameraPermission = rememberCameraPermissionState(),
            currentDocument = DocumentUiModel(listOf()) { _ -> null },
            navigation = dummyNavigation(),
            onStartNewScan = {},
            recentDocuments = listOf(),
            onOpenPdf = {},
        )
    }
}

@Preview
@Composable
fun HomeScreenPreviewWithCurrentDocument() {
    MyScanTheme {
        HomeScreen(
            cameraPermission = rememberCameraPermissionState(),
            currentDocument = fakeDocument(
                listOf("gallica.bnf.fr-bpt6k5530456s-1.jpg"),
                LocalContext.current),
            navigation = dummyNavigation(),
            onStartNewScan = {},
            recentDocuments = listOf(
                RecentDocumentUiState(File("/path/my_file.pdf"), 1755971180000, 3),
                RecentDocumentUiState(File("/path/scan2.pdf"), 1755000500000, 1)
            ),
            onOpenPdf = {},
        )
    }
}
