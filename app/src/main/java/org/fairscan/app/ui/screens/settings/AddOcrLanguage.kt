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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
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
        Text( // TODO externalize
            text = "Language files are downloaded from the official Tesseract project. " +
                    "OCR processing remains fully offline.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(0.dp)
        )
        IconButton(onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close") // TODO Externalize
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        if (suggested != null) {
            item { TextItem("Suggested") }
            item {
                val displayName = suggested.displayName(locale)
                LanguageItem(suggested, displayName, onInstallLanguage)
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        }
        item { TextItem("All languages") }
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
        text = text, // TODO externalize
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
