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
package org.fairscan.app.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fairscan.app.AppContainer
import org.fairscan.app.RecentDocument
import java.io.File

class HomeViewModel(appContainer: AppContainer, appContext: Context): ViewModel() {

    private val recentDocumentsDataStore = appContainer.recentDocumentsDataStore

    val recentDocuments: StateFlow<List<RecentDocumentUiState>> =
        recentDocumentsDataStore.data.map {
            it.documentsList.mapNotNull { doc ->
                var fileName = doc.fileName
                var uri: Uri? = null
                if (doc.fileUri.isNullOrEmpty()) {
                    if (!doc.filePath.isNullOrEmpty()) {
                        val file = File(doc.filePath)
                        uri = file.toUri()
                        fileName = file.name
                    }
                } else {
                    uri = doc.fileUri.toUri()
                }
                if (uri != null) {
                    RecentDocumentUiState(
                        fileUri = uri,
                        fileName = fileName,
                        saveTimestamp = doc.createdAt,
                        pageCount = doc.pageCount,
                    )
                } else null
            }.filter { item -> uriExists(appContext, item.fileUri) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun addRecentDocument(fileUri: Uri, fileName: String, pageCount: Int) {
        viewModelScope.launch {
            recentDocumentsDataStore.updateData { current ->
                val newDoc = RecentDocument.newBuilder()
                    .setFileUri(fileUri.toString())
                    .setFileName(fileName)
                    .setPageCount(pageCount)
                    .setCreatedAt(System.currentTimeMillis())
                    .build()
                current.toBuilder()
                    .addDocuments(0, newDoc)
                    .also { builder ->
                        while (builder.documentsCount > 3) {
                            builder.removeDocuments(builder.documentsCount - 1)
                        }
                    }
                    .build()
            }
        }
    }

    private fun uriExists(context: Context, uri: Uri): Boolean {
        return if (uri.scheme == "file") {
            File(uri.path.orEmpty()).exists()
        } else {
            try {
                DocumentFile.fromSingleUri(context, uri)?.exists() == true
            } catch (_: Exception) {
                false
            }
        }
    }

}
