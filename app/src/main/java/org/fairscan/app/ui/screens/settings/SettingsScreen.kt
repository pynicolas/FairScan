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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.theme.FairScanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onDefaultColorModeChanged: (DefaultColorMode) -> Unit,
    onChooseDirectoryClick: () -> Unit,
    onResetExportDirClick: () -> Unit,
    onExportFormatChanged: (ExportFormat) -> Unit,
    onExportQualityChanged: (ExportQuality) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { BackButton(onBack) },
            )
        }
    ) { paddingValues ->
        SettingsContent(
            uiState,
            onDefaultColorModeChanged,
            onChooseDirectoryClick,
            onResetExportDirClick,
            onExportFormatChanged,
            onExportQualityChanged,
            modifier = Modifier.padding(paddingValues))
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onDefaultColorModeChanged: (DefaultColorMode) -> Unit,
    onChooseDirectoryClick: () -> Unit,
    onResetExportDirClick: () -> Unit,
    onExportFormatChanged: (ExportFormat) -> Unit,
    onExportQualityChanged: (ExportQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (folderLabel, folderLabelColor) = when {
        uiState.exportDirUri == null ->
            stringResource(R.string.download_dirname) to
                    MaterialTheme.colorScheme.onSurface

        uiState.exportDirName != null ->
            uiState.exportDirName to
                    MaterialTheme.colorScheme.onSurface

        else ->
            stringResource(R.string.export_folder_permission_lost) to
                    MaterialTheme.colorScheme.error
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(vertical = 8.dp, horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val context = LocalResources.current

        Text(stringResource(R.string.settings_section_scan), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        RadioButtonGroup(
            R.string.color_mode_default,
            DefaultColorMode.entries,
            onClick = onDefaultColorModeChanged,
            label = { t -> context.getString(t.labelResource) },
            selectedValue = uiState.defaultColorMode
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.settings_section_export), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        DirectorySettingItem(
            label = stringResource(R.string.export_directory),
            folderLabel,
            folderLabelColor,
            onClick = onChooseDirectoryClick,
        )

        Spacer(Modifier.height(12.dp))

        if (uiState.exportDirUri != null) {
            OutlinedButton(
                onClick = onResetExportDirClick,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.reset_to_default))
            }
        }

        Spacer(Modifier.height(16.dp))

        RadioButtonGroup(
            R.string.export_quality,
            ExportQuality.entries.reversed(),
            onClick = onExportQualityChanged,
            label = { t -> context.getString(t.labelResource) },
            selectedValue = uiState.exportQuality
        )

        Spacer(Modifier.height(32.dp))

        RadioButtonGroup(
            R.string.export_format,
            ExportFormat.entries,
            onClick = onExportFormatChanged,
            label = { t -> t.name},
            selectedValue = uiState.exportFormat
        )
    }
}

@Composable
fun <T: Enum<T>> RadioButtonGroup(
    title: Int,
    entries: List<T>,
    onClick: (T) -> Unit,
    label: (T) -> String,
    selectedValue: T,
) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    entries.forEach { t ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(t) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedValue == t,
                onClick = null,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)
            )
            Text(label(t))
        }
    }
}

@Composable
fun DirectorySettingItem(
    label: String,
    folderLabel: String,
    folderLabelColor: Color,
    onClick: () -> Unit,

    ) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
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
                    text = folderLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = folderLabelColor,
                )

                Icon(
                    Icons.Default.Folder,
                    contentDescription = stringResource(R.string.change_directory),
                )
            }
        }
    }
}

@Preview
@Preview(heightDp = 780)
@Composable
fun SettingsScreenPreviewWithoutDir() {
    SettingsScreenPreview(SettingsUiState())
}

@Preview
@Composable
fun SettingsScreenPreviewWithDir() {
    SettingsScreenPreview(SettingsUiState(exportDirUri = "content://root/dir"))
}

@Composable
fun SettingsScreenPreview(uiState: SettingsUiState) {
    FairScanTheme {
        SettingsScreen(
            uiState,
            onDefaultColorModeChanged = {},
            onChooseDirectoryClick = {},
            onResetExportDirClick = {},
            onExportFormatChanged = {},
            onExportQualityChanged = {},
            onBack = {}
        )
    }
}
