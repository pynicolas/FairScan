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
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fairscan.app.RecentDocument
import org.fairscan.app.RecentDocuments
import org.fairscan.app.data.recentDocumentsDataStore
import org.fairscan.app.ui.state.RecentDocumentUiState
import java.io.File

class HomeViewModel(private val recentDocumentsDataStore: DataStore<RecentDocuments>): ViewModel() {

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return HomeViewModel(context.recentDocumentsDataStore) as T
            }
        }
    }

    val recentDocuments: StateFlow<List<RecentDocumentUiState>> =
        recentDocumentsDataStore.data.map {
            it.documentsList.map {
                    doc ->
                RecentDocumentUiState(
                    file = File(doc.filePath),
                    saveTimestamp = doc.createdAt,
                    pageCount = doc.pageCount,
                )
            }.filter { doc -> doc.file.exists() }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    fun addRecentDocument(filePath: String, pageCount: Int) {
        viewModelScope.launch {
            recentDocumentsDataStore.updateData { current ->
                val newDoc = RecentDocument.newBuilder()
                    .setFilePath(filePath)
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

}
