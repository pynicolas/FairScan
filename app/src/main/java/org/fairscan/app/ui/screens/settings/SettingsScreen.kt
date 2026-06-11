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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.components.ConfirmationDialog
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
    onInstallOcrLanguage: (String) -> Unit,
    onCancelOcrDownload: () -> Unit,
    onEnableOcrLanguage: (String, Boolean) -> Unit,
    onDeleteUnusedOcrLanguages: () -> Unit,
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
            onInstallOcrLanguage,
            onCancelOcrDownload,
            onEnableOcrLanguage,
            onDeleteUnusedOcrLanguages,
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
    onInstallOcrLanguage: (String) -> Unit,
    onCancelOcrDownload: () -> Unit,
    onEnableOcrLanguage: (String, Boolean) -> Unit,
    onDeleteUnusedOcrLanguages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddLanguageSheet by remember { mutableStateOf(false) }
    val showDeleteLanguageDialog = rememberSaveable { mutableStateOf(false) }
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

        if (export.dirUri != null) {
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
            selectedValue = export.quality
        )

        Spacer(Modifier.height(32.dp))

        RadioButtonGroup(
            R.string.export_format,
            ExportFormat.entries,
            onClick = onExportFormatChanged,
            label = { t -> t.name},
            selectedValue = export.format
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_section_ocr),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.installedOcrLanguages.map { OcrLanguage(it) }
                .sortedBy { it.displayName(displayLocale) }
                .forEach { lang ->
                    val code = lang.code
                    val selected = code in uiState.enabledOcrLanguages
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onEnableOcrLanguage(code, code !in uiState.enabledOcrLanguages)
                        },
                        leadingIcon = { if (selected) Icon(Icons.Default.Check, contentDescription = null) },
                        label = { Text(lang.displayName(displayLocale)) }
                    )
                }
        }
        Row (verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { showAddLanguageSheet = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_ocr_add_language))
            }
            if (uiState.enabledOcrLanguages != uiState.installedOcrLanguages) {
                TextButton(onClick = { showDeleteLanguageDialog.value = true }) {
                    Text(stringResource(R.string.settings_ocr_clean_up))
                }
            }
        }
    }
    if (showAddLanguageSheet) {
        val onDismiss = { showAddLanguageSheet = false }
        ModalBottomSheet(onDismissRequest = onDismiss) {
            AddLanguageBottomSheetContent(uiState, onDismiss) { code ->
                onInstallOcrLanguage(code)
                showAddLanguageSheet = false
            }
        }
    }
    if (showDeleteLanguageDialog.value) {
        val toRemove = uiState.installedOcrLanguages
            .filterNot { code -> code in uiState.enabledOcrLanguages}
            .joinToString(" • ") { OcrLanguage(it).displayName(displayLocale) }
        ConfirmationDialog(
            title = "Do you want to remove the following languages?",
            message = toRemove,
            showDialog = showDeleteLanguageDialog,
            onConfirm = onDeleteUnusedOcrLanguages,
        )
    }
    uiState.currentDownload?.let { download ->
        OcrDownloadDialog(
            state = download,
            onCancel = onCancelOcrDownload,
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
    SettingsScreenPreview(SettingsUiState(installedOcrLanguages = setOf("fra", "eng")))
}

@Preview
@Composable
fun SettingsScreenPreviewWithDir() {
    SettingsScreenPreview(
        SettingsUiState(export= ExportSettingsUiState(dirUri = "content://root/dir"))
    )
}

@Preview
@Composable
fun SettingsScreenPreviewWithDownloadDialog() {
    SettingsScreenPreview(
        SettingsUiState(currentDownload =
            OcrDownloadUiState(OcrLanguage("eng"), 500_000, 1_200_000)
        )
    )
}

@Preview
@Composable
fun SettingsScreenPreviewWithDownloadError() {
    SettingsScreenPreview(
        SettingsUiState(currentDownload =
            OcrDownloadUiState(OcrLanguage("eng"), failed = true)
        )
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
            onInstallOcrLanguage = {},
            onCancelOcrDownload = {},
            onEnableOcrLanguage = { _,_->},
            onDeleteUnusedOcrLanguages = {},
            onBack = {}
        )
    }
}
