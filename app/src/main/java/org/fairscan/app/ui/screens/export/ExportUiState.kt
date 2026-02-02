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
package org.fairscan.app.ui.screens.export

import android.net.Uri
import org.fairscan.app.ui.screens.settings.ExportFormat

data class ExportUiState(
    val format: ExportFormat = ExportFormat.PDF,
    val filename: String = "",
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val result: ExportResult? = null,
    val savedBundle: SavedBundle? = null,
    val hasShared: Boolean = false,
    val error: ExportError? = null,
) {
    val hasSavedOrShared get() = savedBundle != null || hasShared
}

data class SavedItem(
    val uri: Uri,
    val fileName: String,
    val format: ExportFormat,
)

data class SavedBundle(
    val items: List<SavedItem>,
    val saveDir: SaveDir? = null,
)

data class SaveDir(
    val uri: Uri,
    val name: String?,
)

sealed class ExportError {

    data class OnPrepare(
        val message: String,
        val throwable: Throwable,
    ) : ExportError()

    data class OnSave(
        val messageRes: Int,
        val saveDir: SaveDir?,
        val throwable: Throwable? = null,
    ): ExportError()
}
