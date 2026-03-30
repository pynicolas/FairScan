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
package org.fairscan.app

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.domain.ScanPage
import org.fairscan.app.ui.NavigationState
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.screens.document.CurrentPageUiState
import org.fairscan.app.ui.screens.document.DocumentUiState
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.state.PageThumbnail
import org.fairscan.imageprocessing.ColorMode
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(val imageRepository: ImageRepository, launchMode: LaunchMode): ViewModel() {

    private val _navigationState = MutableStateFlow(NavigationState.initial(launchMode))
    val currentScreen: StateFlow<Screen> = _navigationState.map { it.current }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _navigationState.value.current)

    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())

    init {
        viewModelScope.launch {
            _pages.value = imageRepository.pages()
        }
    }

    val documentUiModel: StateFlow<DocumentUiModel> =
        _pages.map { pages ->
            pages.map {
                val jpeg = imageRepository.getThumbnail(it.key())
                PageThumbnail(it.key(), jpeg?.toBitmap())
            }.toImmutableList()
        }
        .flowOn(Dispatchers.IO)
        .map { DocumentUiModel(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DocumentUiModel()
        )

    private val _currentPageIndex = MutableStateFlow(0)

    val currentPageUiState: Flow<CurrentPageUiState> =
        combine(_currentPageIndex, _pages) { index, pages -> pages.getOrNull(index) }
            .mapLatest { page ->
                page?.let {
                    CurrentPageUiState(
                        imageRepository.jpegBytes(it.key())?.toBitmap(),
                        page.colorMode
                    )
                } ?: CurrentPageUiState()
            }
            .flowOn(Dispatchers.IO)

    val documentUiState: StateFlow<DocumentUiState> =
        combine(_currentPageIndex, currentPageUiState, documentUiModel) { index, page, document ->
            DocumentUiState(index, page, document)
        }
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                DocumentUiState(0, CurrentPageUiState(), DocumentUiModel())
            )

    fun onPageSelected(index: Int) {
        _currentPageIndex.value = index
    }

    fun navigateTo(destination: Screen) {
        if (destination is Screen.Main.Document) {
            require(_pages.value.isNotEmpty()) {
                "Cannot navigate to DocumentScreen with zero pages"
            }
            _currentPageIndex.value = min(_pages.value.size - 1, destination.initialPage)
        }
        _navigationState.update { it.navigateTo(destination) }
    }

    fun navigateBack() {
        _navigationState.update { stack -> stack.navigateBack() }
    }

    fun rotateImage(id: String, clockwise: Boolean) {
        viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                imageRepository.rotate(id, clockwise)
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    fun movePage(id: String, newIndex: Int) {
        viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                imageRepository.movePage(id, newIndex)
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    fun deletePage(id: String) {
        viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                imageRepository.delete(id)
                imageRepository.pages()
            }

            if (pages.isEmpty()) {
                navigateTo(Screen.Main.Camera)
                _currentPageIndex.value = 0
            } else if (_currentPageIndex.value >= pages.size) {
                _currentPageIndex.value = pages.size - 1
            }
            _pages.value = pages
        }
    }

    fun togglePageColorMode(id: String) {
        viewModelScope.launch {
            val currentColorMode = _pages.value.find { p -> p.id == id }?.colorMode
            currentColorMode?.let {
                val newColorMode =
                    if (it == ColorMode.COLOR) ColorMode.GRAYSCALE else ColorMode.COLOR
                val pages = withContext(Dispatchers.IO) {
                    imageRepository.setColorMode(id, newColorMode)
                    imageRepository.pages()
                }
                _pages.value = pages
            }
        }
    }

    fun startNewDocument() {
        _pages.value = persistentListOf()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                imageRepository.clear()
            }
        }
    }

    fun handleImageCaptured(capturedPage: CapturedPage) {
        viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                val sourceJpeg = capturedPage.sourceJpeg.await()
                imageRepository.add(
                    capturedPage.pageJpeg,
                    sourceJpeg,
                    capturedPage.metadata,
                )
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }
}
