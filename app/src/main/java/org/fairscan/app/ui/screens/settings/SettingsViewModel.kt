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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fairscan.app.AppContainer
import org.fairscan.app.domain.ExportQuality

data class SettingsUiState(
    val exportDirName: String? = null,
    val exportFormat: ExportFormat = ExportFormat.PDF,
    val exportQuality: ExportQuality = ExportQuality.BALANCED,
)

class SettingsViewModel(container: AppContainer) : ViewModel() {

    private val repo = container.settingsRepository

    val uiState = combine(
        repo.exportDirName,
        repo.exportFormat,
        repo.exportQuality,
    ) { dir, format, quality ->
        SettingsUiState(
            exportDirName = dir,
            exportFormat = format,
            exportQuality = quality,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState()
    )

    fun setExportDirUri(uri: String?) {
        viewModelScope.launch {
            repo.setExportDirUri(uri)
        }
    }

    fun setExportFormat(format: ExportFormat) {
        viewModelScope.launch {
            repo.setExportFormat(format)
        }
    }

    fun setExportQuality(quality: ExportQuality) {
        viewModelScope.launch {
            repo.setExportQuality(quality)
        }
    }
}
