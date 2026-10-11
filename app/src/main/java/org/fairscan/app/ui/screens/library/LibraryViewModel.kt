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
package org.fairscan.app.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fairscan.app.data.DocumentLibrary
import org.fairscan.app.data.LibraryDocument
import org.fairscan.app.data.Logger

data class LibraryUiState(
    val documents: List<LibraryDocument> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

class LibraryViewModel(private val library: DocumentLibrary, private val logger: Logger) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, failed = false) }
            try {
                _uiState.value = LibraryUiState(documents = library.documents(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Library", "Cannot load saved documents", e)
                _uiState.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        performLibraryAction("Cannot import selected documents") {
            library.importDocuments(uris)
        }
    }

    fun deleteDocuments(documents: List<LibraryDocument>) {
        if (documents.isEmpty()) return
        performLibraryAction("Cannot delete selected documents") {
            library.deleteDocuments(documents)
        }
    }

    fun deleteDocument(document: LibraryDocument) = deleteDocuments(listOf(document))

    fun renameDocument(document: LibraryDocument, name: String) {
        performLibraryAction("Cannot rename document") {
            library.renameDocument(document, name)
        }
    }

    private fun performLibraryAction(message: String, action: suspend () -> Unit) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, failed = false) }
            try {
                action()
                _uiState.value = LibraryUiState(documents = library.documents(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Library", message, e)
                _uiState.update { it.copy(loading = false, failed = true) }
            }
        }
    }
}
