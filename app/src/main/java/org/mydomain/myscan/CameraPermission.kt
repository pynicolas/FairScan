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
package org.mydomain.myscan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private fun hasCameraPermission(context: Context): Boolean {
    val camera = Manifest.permission.CAMERA
    return ContextCompat.checkSelfPermission(context, camera) == PackageManager.PERMISSION_GRANTED
}

@Stable
class CameraPermissionState internal constructor(
    private val context: Context,
    private val launcher: ManagedActivityResultLauncher<String, Boolean>
) {
    var isGranted by mutableStateOf(hasCameraPermission(context))
        private set

    fun request() {
        launcher.launch(Manifest.permission.CAMERA)
    }

    internal fun update(granted: Boolean) {
        isGranted = granted
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.camera_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    lateinit var state: CameraPermissionState

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        state.update(granted)
    }

    state = remember {
        CameraPermissionState(context, launcher)
    }

    return state
}
