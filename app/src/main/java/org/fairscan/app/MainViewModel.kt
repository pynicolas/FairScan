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
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.ui.NavigationState
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.imageprocessing.encodeJpeg
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors

class MainViewModel(val imageRepository: ImageRepository, launchMode: LaunchMode): ViewModel() {

    // TODO ImageRepository should be made thread-safe
    private val repositoryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val _navigationState = MutableStateFlow(NavigationState.initial(launchMode))
    val currentScreen: StateFlow<Screen> = _navigationState.map { it.current }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _navigationState.value.current)

    private val _pages = MutableStateFlow(imageRepository.pages())
    val documentUiModel: StateFlow<DocumentUiModel> =
        _pages.map { pages ->
            DocumentUiModel(
                pageKeys = pages.map { p ->
                    PageViewKey(p.id, p.manualRotation)
                }.toImmutableList(),
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
            val pages = withContext(repositoryDispatcher) {
                imageRepository.rotate(id, clockwise)
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    fun movePage(id: String, newIndex: Int) {
        viewModelScope.launch {
            val pages = withContext(repositoryDispatcher) {
                imageRepository.movePage(id, newIndex)
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    fun deletePage(id: String) {
        viewModelScope.launch {
            val pages = withContext(repositoryDispatcher) {
                imageRepository.delete(id)
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    fun startNewDocument() {
        _pages.value = persistentListOf()
        viewModelScope.launch {
            withContext(repositoryDispatcher) {
                imageRepository.clear()
            }
        }
    }

    fun getBitmap(key: PageViewKey): Bitmap? {
        val bytes = imageRepository.jpegBytes(key)
        return bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    fun getThumbnail(key: PageViewKey): Bitmap? {
        val bytes = imageRepository.getThumbnail(key)
        return bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    fun handleImageCaptured(capturedPage: CapturedPage) {
        viewModelScope.launch {
            val pages = withContext(repositoryDispatcher) {
                imageRepository.add(
                    capturedPage.pageJpeg,
                    compressJpeg(capturedPage.source, 90),
                    capturedPage.metadata,
                )
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        return try {
            encodeJpeg(bgr, quality)
        } finally {
            bgr.release()
        }
    }
}
