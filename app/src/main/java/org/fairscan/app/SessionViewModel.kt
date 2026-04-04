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

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.platform.ImageProcessor
import java.io.File
import java.util.UUID

class SessionViewModel(
    app: Application,
    val launchMode: LaunchMode,
    appContainer: AppContainer
) : AndroidViewModel(app) {

    val sessionDir: File = when (launchMode) {
        LaunchMode.NORMAL ->
            app.filesDir

        LaunchMode.EXTERNAL_SCAN_TO_PDF ->
            File(appContainer.sessionsRoot(), UUID.randomUUID().toString())
                .apply { mkdirs() }
    }

    private val sessionContainer = ScanSessionContainer(
        context = app,
        scanRootDir = sessionDir,
        scope = viewModelScope,
    )

    val imageRepository: ImageRepository = sessionContainer.imageRepository

    override fun onCleared() {
        super.onCleared()

        if (launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) {
            sessionDir.deleteRecursively()
        }
    }
}

class ScanSessionContainer(
    context: Context,
    scanRootDir: File,
    scope: CoroutineScope,
) {
    private val density = context.resources.displayMetrics.density
    private val thumbnailSizePx = (THUMBNAIL_SIZE_DP * density).toInt()

    val imageRepository = ImageRepository(
        scanRootDir,
        ImageProcessor(thumbnailSizePx),
        scope,
    )
}
