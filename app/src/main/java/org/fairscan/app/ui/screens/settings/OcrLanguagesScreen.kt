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

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.data.OcrLanguage
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.theme.FairScanTheme
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class LanguageState {
    ACTIVE,
    INACTIVE,
    NOT_INSTALLED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrLanguagesScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onLanguageClick: (String) -> Unit,
    onRemoveLanguage: (String) -> Unit,
    onCancelOcrDownload: () -> Unit,
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_ocr_languages)) },
                navigationIcon = { BackButton(onBack) },
            )
        }
    ) { paddingValues ->
        OcrLanguagesContent(
            uiState = uiState,
            onLanguageClick = onLanguageClick,
            onCancelOcrDownload = onCancelOcrDownload,
            onRemoveLanguage = onRemoveLanguage,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun OcrLanguagesContent(
    uiState: SettingsUiState,
    onLanguageClick: (String) -> Unit,
    onRemoveLanguage: (String) -> Unit,
    onCancelOcrDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.current.platformLocale

    val installed = remember(
        uiState.installedOcrLanguages,
        uiState.enabledOcrLanguages,
        locale,
    ) {
        uiState.installedOcrLanguages
            .map { OcrLanguage(it) }
            .sortedWith(compareBy { it.displayName(locale) })
    }

    val available = remember(
        uiState.installedOcrLanguages,
        locale,
    ) {
        OcrLanguage.AVAILABLE_LANGUAGE_CODES
            .filterNot { it in uiState.installedOcrLanguages }
            .map { OcrLanguage(it) }
            .sortedBy { it.displayName(locale) }
    }

    val suggested = available.firstOrNull {
        it.locale.displayLanguage == locale.displayLanguage
    }

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_ocr_download_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (installed.isNotEmpty()) {
            item {
                SectionTitle(
                    stringResource(R.string.settings_ocr_languages_installed)
                )
            }

            items(
                items = installed,
                key = { it.code }
            ) { lang ->
                LanguageItem(
                    language = lang,
                    state = if (lang.code in uiState.enabledOcrLanguages) {
                        LanguageState.ACTIVE
                    } else {
                        LanguageState.INACTIVE
                    },
                    onClick = { onLanguageClick(lang.code) },
                    onRemove = { onRemoveLanguage(lang.code) }
                )
            }
        }

        if (suggested != null) {
            item {
                SectionTitle(
                    stringResource(R.string.settings_ocr_suggested)
                )
            }

            item {
                LanguageItem(
                    language = suggested,
                    state = LanguageState.NOT_INSTALLED,
                    onClick = {
                        onLanguageClick(suggested.code)
                    }
                )
            }
        }

        item {
            SectionTitle(
                stringResource(R.string.settings_ocr_languages_available)
            )
        }

        items(
            available.filter { it != suggested },
            key = { it.code }
        ) { lang ->
            LanguageItem(
                language = lang,
                state = LanguageState.NOT_INSTALLED,
                onClick = {
                    onLanguageClick(lang.code)
                }
            )
        }
    }
    uiState.currentDownload?.let { download ->
        OcrDownloadDialog(
            state = download,
            onCancel = onCancelOcrDownload,
        )
    }
}

@Composable
private fun LanguageItem(
    language: OcrLanguage,
    state: LanguageState,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val locale = Locale.current.platformLocale

    ListItem(
        headlineContent = {
            Text(language.displayName(locale))
        },
        leadingContent = {
            when (state) {
                LanguageState.ACTIVE ->
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null
                    )

                LanguageState.INACTIVE ->
                    Icon(
                        Icons.Default.RadioButtonUnchecked,
                        contentDescription = null
                    )

                LanguageState.NOT_INSTALLED ->
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null
                    )
            }
        },
        trailingContent = {
            if (onRemove != null) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SectionTitle(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(
            horizontal = 16.dp,
            vertical = 16.dp,
        )
    )
}

@Composable
fun OcrDownloadDialog(
    state: OcrDownloadUiState,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {}, // tapping outside the dialog should not cancel
        title = {
            Text(
                stringResource(R.string.settings_ocr_downloading),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            if (state.failed) {
                Text(
                    text = stringResource(R.string.settings_ocr_download_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Column {
                    Text(state.language.displayName(Locale.current.platformLocale))
                    Spacer(Modifier.height(16.dp))

                    val progress =
                        state.totalBytes?.let { total ->
                            state.downloadedBytes.toFloat() / total
                        }

                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        buildProgressText(
                            state.downloadedBytes,
                            state.totalBytes,
                            LocalContext.current
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(if (state.failed) R.string.close else R.string.cancel))
            }
        },
        confirmButton = {}
    )
}

private fun buildProgressText(
    downloadedBytes: Long,
    totalBytes: Long?,
    context: Context,
): String {
    return if (totalBytes != null) {
        listOf(
            Formatter.formatShortFileSize(context, downloadedBytes),
            Formatter.formatShortFileSize(context, totalBytes),
        ).joinToString(" / ")
    } else {
        Formatter.formatShortFileSize(context, downloadedBytes)
    }
}

@Preview
@Composable
fun OcrLanguagesScreenPreviewBasic() {
    OcrLanguagesScreenPreview(
        SettingsUiState(
            installedOcrLanguages = setOf("fra", "deu"),
            enabledOcrLanguages = setOf("fra")
        )
    )
}

@Preview
@Composable
fun OcrLanguagesScreenPreviewWithDownloadDialog() {
    OcrLanguagesScreenPreview(
        SettingsUiState(currentDownload =
            OcrDownloadUiState(OcrLanguage("eng"), 500_000, 1_200_000)
        )
    )
}

@Preview
@Composable
fun OcrLanguagesScreenPreviewWithDownloadError() {
    OcrLanguagesScreenPreview(
        SettingsUiState(currentDownload =
            OcrDownloadUiState(OcrLanguage("eng"), failed = true)
        )
    )
}

@Composable
fun OcrLanguagesScreenPreview(uiState: SettingsUiState) {
    FairScanTheme {
        OcrLanguagesScreen(uiState, {}, {}, {}, {})
    }
}
