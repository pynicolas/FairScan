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
package org.fairscan.app.ui

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.LaunchMode
import org.fairscan.app.ui.Screen.Main.Camera
import org.fairscan.app.ui.Screen.Main.Document
import org.fairscan.app.ui.Screen.Main.Export
import org.fairscan.app.ui.Screen.Main.Home
import org.fairscan.app.ui.Screen.Overlay.About
import org.fairscan.app.ui.Screen.Overlay.Libraries
import org.junit.Test

class NavigationTest {

    @Test
    fun empty_ScreenStack() {
        val empty = NavigationState.initial(LaunchMode.NORMAL)
        assertThat(empty.current).isEqualTo(Home)
        assertThat(empty.navigateBack()).isEqualTo(empty)
    }

    @Test
    fun navigate_between_fixed_screens() {
        val atHome = NavigationState.initial(LaunchMode.NORMAL)
        val atCamera = atHome.navigateTo(Camera)
        val atDocument = atHome.navigateTo(Document())
        val atExport = atHome.navigateTo(Export)

        assertThat(atHome.current).isEqualTo(Home)
        assertThat(atCamera.current).isEqualTo(Camera)
        assertThat(atDocument.current).isEqualTo(Document())
        assertThat(atExport.current).isEqualTo(Export)

        assertThat(atCamera.navigateTo(Document())).isEqualTo(atDocument)
        assertThat(atDocument.navigateTo(Home)).isEqualTo(atHome)
        assertThat(atDocument.navigateTo(Camera)).isEqualTo(atCamera)

        assertThat(atHome.navigateBack()).isEqualTo(atHome)
        assertThat(atCamera.navigateBack()).isEqualTo(atHome)
        assertThat(atDocument.navigateBack()).isEqualTo(atCamera)
        assertThat(atExport.navigateBack()).isEqualTo(atDocument)
    }

    @Test
    fun navigate_to_secondary_screens() {
        val atHome = NavigationState.initial(LaunchMode.NORMAL)
        val atCamera = atHome.navigateTo(Camera)

        val atAboutAfterHome = atHome.navigateTo(About)
        assertThat(atAboutAfterHome.current).isEqualTo(About)
        assertThat(atAboutAfterHome.navigateBack()).isEqualTo(atHome)

        val atAboutAfterCamera = atCamera.navigateTo(About)
        assertThat(atAboutAfterCamera.current).isEqualTo(About)
        assertThat(atAboutAfterCamera.navigateBack()).isEqualTo(atCamera)

        val atLibrariesAfterCameraAbout = atAboutAfterCamera.navigateTo(Libraries)
        assertThat(atLibrariesAfterCameraAbout.current).isEqualTo(Libraries)
        assertThat(atLibrariesAfterCameraAbout.navigateBack()).isEqualTo(atAboutAfterCamera)
    }

    @Test
    fun external_call() {
        val initial = NavigationState.initial(LaunchMode.EXTERNAL_SCAN_TO_PDF)
        assertThat(initial.current).isEqualTo(Camera)
        assertThat(initial.navigateBack().current).isEqualTo(Camera)
    }
}