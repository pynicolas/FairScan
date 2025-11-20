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
package org.fairscan.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.recentDocumentsDataStore
import org.fairscan.app.ui.NavigationState
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.state.RecentDocumentUiState
import java.io.File

class MainViewModel(
    private val imageRepository: ImageRepository,
    private val recentDocumentsDataStore: DataStore<RecentDocuments>,
): ViewModel() {

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = context.applicationContext as FairScanApp
                return MainViewModel(
                    app.appContainer.imageRepository,
                    context.recentDocumentsDataStore,
                ) as T
            }
        }
    }

    private val _navigationState = MutableStateFlow(NavigationState.initial())
    val currentScreen: StateFlow<Screen> = _navigationState.map { it.current }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _navigationState.value.current)

    private val _pageIds = MutableStateFlow(imageRepository.imageIds())
    val documentUiModel: StateFlow<DocumentUiModel> =
        _pageIds.map { ids ->
            DocumentUiModel(
                pageIds = ids,
                imageLoader = ::getBitmap,
                thumbnailLoader = ::getThumbnail,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DocumentUiModel(persistentListOf(), ::getBitmap, ::getThumbnail)
        )

    fun navigateTo(destination: Screen) {
        _navigationState.update { it.navigateTo(destination) }
    }

    fun navigateBack() {
        _navigationState.update { stack -> stack.navigateBack() }
    }

    fun rotateImage(id: String, clockwise: Boolean) {
        viewModelScope.launch {
            imageRepository.rotate(id, clockwise)
            _pageIds.value = imageRepository.imageIds()
        }
    }

    fun movePage(id: String, newIndex: Int) {
        imageRepository.movePage(id, newIndex)
        _pageIds.value = imageRepository.imageIds()
    }

    fun deletePage(id: String) {
        imageRepository.delete(id)
        _pageIds.value = imageRepository.imageIds()
    }

    fun startNewDocument() {
        _pageIds.value = persistentListOf()
        viewModelScope.launch {
            imageRepository.clear()
        }
    }

    fun getBitmap(id: String): Bitmap? {
        val bytes = imageRepository.getContent(id)
        return bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    fun getThumbnail(id: String): Bitmap? {
        val bytes = imageRepository.getThumbnail(id)
        return bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
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

    fun handleImageCaptured(jpegBytes: ByteArray) {
        imageRepository.add(jpegBytes)
        _pageIds.value = imageRepository.imageIds()
    }
}
