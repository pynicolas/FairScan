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
package org.fairscan.app.ui.screens.about

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fairscan.app.AppContainer
import org.fairscan.app.BuildConfig
import org.fairscan.app.data.ImageRepository
import java.time.LocalDateTime

sealed interface AboutEvent {
    data class CopyLogs(val logs: String) : AboutEvent
    object PrepareEmailWithLastImage : AboutEvent
}

class AboutViewModel(container: AppContainer, val imageRepository: ImageRepository): ViewModel() {

    private val logRepository = container.logRepository

    private val _events = MutableSharedFlow<AboutEvent>()
    val events = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState = _uiState.asStateFlow()

    fun onCopyLogsClicked() {
        viewModelScope.launch {
            val logs = buildFullLogs()
            _events.emit(AboutEvent.CopyLogs(logs))
        }
    }

    private fun buildFullLogs(): String {
        val header = buildString {
            appendLine("FairScan diagnostics report")
            appendLine("App version: ${BuildConfig.VERSION_NAME}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Generated: ${LocalDateTime.now()}")
            appendLine()
            appendLine("-- Application logs --")
            appendLine()
        }
        return header + logRepository.getLogs()
    }

    fun refreshLastCapturedImageState() {
        _uiState.update {
            it.copy(hasLastCapturedImage = imageRepository.lastAddedSourceFile() != null)
        }
    }

    fun onContactWithLastImageClicked() {
        viewModelScope.launch {
            _events.emit(AboutEvent.PrepareEmailWithLastImage)
        }
    }
}
