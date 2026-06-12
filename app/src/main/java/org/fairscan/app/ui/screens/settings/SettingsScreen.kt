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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.data.OcrLanguage
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.dummyNavigation
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
    navigation: Navigation,
) {
    BackHandler { navigation.back() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { BackButton(navigation.back) },
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
            navigation,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onDefaultColorModeChanged: (DefaultColorMode) -> Unit,
    onChooseDirectoryClick: () -> Unit,
    onResetExportDirClick: () -> Unit,
    onExportFormatChanged: (ExportFormat) -> Unit,
    onExportQualityChanged: (ExportQuality) -> Unit,
    navigation: Navigation,
    modifier: Modifier = Modifier,
) {
    val displayLocale = Locale.current.platformLocale
    val export = uiState.export
    val (folderLabel, folderLabelColor) = when {
        export.dirUri == null ->
            stringResource(R.string.download_dirname) to
                    MaterialTheme.colorScheme.onSurface

        export.dirName != null ->
            export.dirName to
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

        SingleChoiceSetting(
            title = stringResource(R.string.color_mode_default),
            entries = DefaultColorMode.entries,
            onValueChanged = onDefaultColorModeChanged,
            label = { t -> context.getString(t.labelResource) },
            selectedValue = uiState.defaultColorMode,
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

        if (export.dirUri != null) {
            TextButton(
                onClick = onResetExportDirClick,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(stringResource(R.string.reset_to_default))
            }
        }

        Spacer(Modifier.height(8.dp))

        SingleChoiceSetting(
            title = stringResource(R.string.export_quality),
            entries = ExportQuality.entries.reversed(),
            selectedValue = export.quality,
            onValueChanged = onExportQualityChanged,
            label = { t -> context.getString(t.labelResource) },
        )

        SingleChoiceSetting(
            title = stringResource(R.string.export_format),
            entries = ExportFormat.entries,
            selectedValue = export.format,
            onValueChanged = onExportFormatChanged,
            label = { it.name },
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_section_ocr),
            style = MaterialTheme.typography.titleLarge
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_ocr_languages)) },
            supportingContent = {
                Text(uiState.enabledOcrLanguages
                    .map { OcrLanguage(it).displayName(displayLocale) }
                    .sorted()
                    .joinToString(" • ")
                    .ifEmpty { stringResource(R.string.settings_ocr_languages_disabled) }
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            },
            modifier = Modifier.clickable { navigation.toOcrLanguagesScreen() }
        )
    }
}

@Composable
fun <T> SingleChoiceSetting(
    title: String,
    entries: List<T>,
    selectedValue: T,
    onValueChanged: (T) -> Unit,
    label: (T) -> String,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(title)
        },
        supportingContent = {
            Text(label(selectedValue))
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable {
            showDialog = true
        }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = {
                Text(title)
            },
            text = {
                Column {
                    entries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChanged(entry)
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedValue == entry,
                                onClick = {
                                    onValueChanged(entry)
                                    showDialog = false
                                }
                            )

                            Text(
                                text = label(entry),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

@Composable
fun DirectorySettingItem(
    label: String,
    folderLabel: String,
    folderLabelColor: Color,
    onClick: () -> Unit,

    ) {
    Column (
        modifier = Modifier.padding(vertical = 0.dp, horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurfaceVariant),
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
    SettingsScreenPreview(SettingsUiState(
        installedOcrLanguages = setOf("fra", "eng", "deu"),
        enabledOcrLanguages = setOf("fra", "eng")
    ))
}

@Preview
@Composable
fun SettingsScreenPreviewWithDir() {
    SettingsScreenPreview(
        SettingsUiState(export= ExportSettingsUiState(dirUri = "content://root/dir"))
    )
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
            navigation = dummyNavigation(),
        )
    }
}
