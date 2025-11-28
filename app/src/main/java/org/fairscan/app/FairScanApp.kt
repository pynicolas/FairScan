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

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import org.fairscan.app.data.FileLogger
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.LogRepository
import org.fairscan.app.data.PdfFileManager
import org.fairscan.app.data.recentDocumentsDataStore
import org.fairscan.app.domain.ImageSegmentationService
import org.fairscan.app.platform.AndroidPdfWriter
import org.fairscan.app.platform.OpenCvTransformations
import org.fairscan.app.ui.screens.about.AboutViewModel
import org.fairscan.app.ui.screens.camera.CameraViewModel
import org.fairscan.app.ui.screens.export.ExportViewModel
import org.fairscan.app.ui.screens.home.HomeViewModel
import org.fairscan.app.ui.screens.settings.SettingsRepository
import org.fairscan.app.ui.screens.settings.SettingsViewModel
import java.io.File

class FairScanApp : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}

const val THUMBNAIL_SIZE_DP = 120

class AppContainer(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val thumbnailSizePx = (THUMBNAIL_SIZE_DP * density).toInt()
    val imageRepository = ImageRepository(context.filesDir, OpenCvTransformations(), thumbnailSizePx)
    val pdfFileManager = PdfFileManager(
        File(context.cacheDir, "pdfs"),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        AndroidPdfWriter()
    )
    val logRepository = LogRepository(File(context.filesDir, "logs.txt"))
    val logger = FileLogger(logRepository)
    val imageSegmentationService = ImageSegmentationService(context, logger)
    val recentDocumentsDataStore = context.recentDocumentsDataStore
    val settingsRepository = SettingsRepository(context)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified VM : ViewModel> viewModelFactory(
        crossinline create: (AppContainer) -> VM
    ) = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return create(this@AppContainer) as T
        }
    }

    val mainViewModelFactory = viewModelFactory { MainViewModel(it) }
    val homeViewModelFactory = viewModelFactory { HomeViewModel(it, context) }
    val cameraViewModelFactory = viewModelFactory { CameraViewModel(it) }
    val exportViewModelFactory = viewModelFactory { ExportViewModel(it) }
    val aboutViewModelFactory = viewModelFactory { AboutViewModel(it) }
    val settingsViewModelFactory = viewModelFactory { SettingsViewModel(it) }
}
