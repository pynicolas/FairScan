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
package org.fairscan.app.ui.screens.settings


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.theme.FairScanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onChooseDirectoryClick: () -> Unit,
    onResetExportDirClick: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { BackButton(onBack) },
            )
        }
    ) { paddingValues ->
        SettingsContent(uiState, onChooseDirectoryClick, onResetExportDirClick, modifier = Modifier.padding(paddingValues))
    }


}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onChooseDirectoryClick: () -> Unit,
    onResetExportDirClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folderName = remember(uiState.exportDirUri) {
        extractFolderName(uiState.exportDirUri)
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        DirectorySettingItem(
            label = "Export directory",
            folderName = folderName,
            onClick = onChooseDirectoryClick
        )

        Spacer(Modifier.height(12.dp))

        if (uiState.exportDirUri != null) {
            OutlinedButton(
                onClick = onResetExportDirClick,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text("Reset to default")
            }
        }
    }
}

@Composable
fun DirectorySettingItem(
    label: String,
    folderName: String,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.bodyLarge
                )

                Icon(
                    Icons.Default.Folder,
                    contentDescription = "Change directory",
                )
            }
        }
    }
}

private fun extractFolderName(uriString: String?): String {
    if (uriString == null) return "Downloads (default)"
    return runCatching {
        val uri = uriString.toUri()
        uri.lastPathSegment?.substringAfter(':')?.substringAfter('/') ?: uriString
    }.getOrElse { uriString }
}

@Preview
@Composable
fun SettingsScreenPreviewWithoutDir() {
    SettingsScreenPreview(SettingsUiState(null))
}

@Preview
@Composable
fun SettingsScreenPreviewWithDir() {
    SettingsScreenPreview(SettingsUiState("content://root/dir"))
}

@Composable
fun SettingsScreenPreview(uiState: SettingsUiState) {
    FairScanTheme {
        SettingsScreen(uiState, onChooseDirectoryClick = {}, onResetExportDirClick = {}, onBack= {})
    }
}
