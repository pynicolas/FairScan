/*
 * Copyright 2025 Pierre-Yves Nicolas
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
package org.fairscan.app.ui

import android.net.Uri
import org.fairscan.app.GeneratedPdf
import java.io.File

data class PdfGenerationUiState(
    val isGenerating: Boolean = false,
    val generatedPdf: GeneratedPdf? = null,
    val desiredFilename: String = "",
    val savedFileUri: Uri? = null,
    val hasSharedPdf: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasSavedOrSharedPdf get() = savedFileUri != null || hasSharedPdf
}

data class RecentDocumentUiState(
    val file: File,
    val saveTimestamp: Long,
    val pageCount: Int,
)
