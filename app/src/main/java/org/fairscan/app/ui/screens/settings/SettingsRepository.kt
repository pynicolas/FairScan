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

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.fairscan.app.domain.ExportQuality

private val Context.dataStore by preferencesDataStore(name = "fairscan_settings")

class SettingsRepository(private val context: Context) {

    private val EXPORT_DIR_URI = stringPreferencesKey("export_dir_uri")
    private val EXPORT_FORMAT = stringPreferencesKey("export_format")
    private val EXPORT_QUALITY = stringPreferencesKey("export_quality")

    val exportDirUri: Flow<String?> =
        context.dataStore.data.map { prefs ->
            prefs[EXPORT_DIR_URI]
        }

    val exportDirName: Flow<String?> = exportDirUri.map { uriString ->
        uriString?.let {
            DocumentFile.fromTreeUri(context, it.toUri())?.name
        }
    }

    val exportFormat: Flow<ExportFormat> =
        context.dataStore.data.map { prefs ->
            when (prefs[EXPORT_FORMAT]) {
                "JPEG" -> ExportFormat.JPEG
                "PDF", null -> ExportFormat.PDF
                else -> ExportFormat.PDF
            }
        }

    val exportQuality: Flow<ExportQuality> =
        context.dataStore.data.map { prefs ->
            when (prefs[EXPORT_QUALITY]) {
                "LOW" -> ExportQuality.LOW
                "HIGH" -> ExportQuality.HIGH
                "BALANCED", null -> ExportQuality.BALANCED
                else -> ExportQuality.BALANCED
            }
        }

    suspend fun setExportDirUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) {
                prefs.remove(EXPORT_DIR_URI)
            } else {
                prefs[EXPORT_DIR_URI] = uri
            }
        }
    }

    suspend fun setExportFormat(format: ExportFormat) {
        context.dataStore.edit { prefs ->
            prefs[EXPORT_FORMAT] = format.name
        }
    }

    suspend fun setExportQuality(quality: ExportQuality) {
        context.dataStore.edit { prefs ->
            prefs[EXPORT_QUALITY] = quality.name
        }
    }
}

enum class ExportFormat(val mimeType: String) {
    PDF("application/pdf"),
    JPEG("image/jpeg"),
}
