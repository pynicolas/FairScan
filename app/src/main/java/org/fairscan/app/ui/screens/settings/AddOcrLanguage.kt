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
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.data.OcrLanguage

@Composable
fun AddLanguageBottomSheetContent(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onInstallLanguage: (String) -> Unit,
) {
    val locale = Locale.current.platformLocale

    val availableLanguages = remember(uiState.installedOcrLanguages, locale) {
        OcrLanguage.AVAILABLE_LANGUAGE_CODES
            .filter { it !in uiState.installedOcrLanguages }
            .map { code ->
                val language = OcrLanguage(code)
                language to language.displayName(locale)
            }
            .sortedBy { (_, name) -> name }
    }

    val deviceLanguage = locale.displayLanguage
    val suggested =
        availableLanguages
            .map { it.first }
            // Comparing language code doesn't work for some languages (e.g. English)
            // because we compare a 2-letter code with a 3-letter one (coming from Tesseract).
            .firstOrNull { it.locale.displayLanguage == deviceLanguage }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 0.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_ocr_download_intro),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(0.dp)
        )
        IconButton(onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        if (suggested != null) {
            item { TextItem(stringResource(R.string.settings_ocr_suggested)) }
            item {
                val displayName = suggested.displayName(locale)
                LanguageItem(suggested, displayName, onInstallLanguage)
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        }
        item { TextItem(stringResource(R.string.settings_ocr_all_languages)) }
        items(
            availableLanguages.filter { (lang, _) -> lang != suggested },
            key = { (lang, _) -> lang.code }
        ) { (lang, displayName) ->
            LanguageItem(lang, displayName, onInstallLanguage)
        }
    }
}

@Composable
private fun TextItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
private fun LanguageItem(
    lang: OcrLanguage,
    displayName: String,
    onInstallLanguage: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(displayName) },
        trailingContent = {
            Icon(Icons.Default.Download, contentDescription = null)
        },
        modifier = Modifier.clickable { onInstallLanguage(lang.code) }
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
