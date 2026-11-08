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
package org.fairscan.app.ui.screens.camera

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fairscan.app.MainActivity
import org.fairscan.app.MainViewModel
import org.fairscan.app.ui.Screen
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TorchLifecycleTest {
    @Test
    fun preservesTorchPreferenceAcrossStopStart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.CAMERA"
            )
        ).use { it.readBytes() }
        val provider = ProcessCameraProvider.getInstance(context).get()
        assumeTrue(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
        val cameraInfo = provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
        assumeTrue(cameraInfo.hasFlashUnit())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var mainViewModel: MainViewModel
            lateinit var cameraViewModel: CameraViewModel
            scenario.onActivity { activity ->
                mainViewModel = ViewModelProvider(activity)[MainViewModel::class.java]
                cameraViewModel = ViewModelProvider(activity)[CameraViewModel::class.java]
            }
            waitUntil("initial navigation") { mainViewModel.currentScreen.value != null }
            scenario.onActivity { mainViewModel.navigateTo(Screen.Main.Camera) }
            waitUntil("camera open") { cameraInfo.cameraState.value?.type == CameraState.Type.OPEN }
            scenario.onActivity { cameraViewModel.setTorchEnabled(true) }
            waitUntil("torch on") { cameraInfo.torchState.value == TorchState.ON }

            repeat(2) {
                scenario.moveToState(Lifecycle.State.CREATED)
                waitUntil("camera closed") { cameraInfo.cameraState.value?.type == CameraState.Type.CLOSED }
                waitUntil("torch off while stopped") { cameraInfo.torchState.value == TorchState.OFF }
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitUntil("torch restored") { cameraInfo.torchState.value == TorchState.ON }
                assertTrue(cameraViewModel.isTorchEnabled.value)
            }

            scenario.onActivity { cameraViewModel.setTorchEnabled(false) }
            waitUntil("torch disabled") { cameraInfo.torchState.value == TorchState.OFF }
            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil("camera closed") { cameraInfo.cameraState.value?.type == CameraState.Type.CLOSED }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil("camera reopened") { cameraInfo.cameraState.value?.type == CameraState.Type.OPEN }
            instrumentation.waitForIdleSync()
            waitUntil("torch still disabled") { cameraInfo.torchState.value == TorchState.OFF }
        }
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            assertTrue("Timed out waiting for $description", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(50)
        }
    }
}
