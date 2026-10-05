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
package org.fairscan.app.ui.screens.settings

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.ui.screens.settings.DefaultFilenameStyle.SCAN_DATE_TIME
import org.fairscan.app.ui.screens.settings.DefaultFilenameStyle.DATE
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsRepositoryTest {

    @Test
    fun default_filename_styles() {
        val dec31 = SimpleDateFormat("yyyy_MM_dd HH:mm:ss", Locale.ENGLISH)
            .parse("2025_12_31 15:59:03")
        requireNotNull(dec31)
        assertThat(SCAN_DATE_TIME.filename()).isEqualTo(SCAN_DATE_TIME.filename(Date()))
        assertThat(SCAN_DATE_TIME.filename(dec31)).isEqualTo("Scan 2025-12-31 15.59.03")
        assertThat(DATE.filename()).isEqualTo(DATE.filename(Date()))
        assertThat(DATE.filename(dec31)).isEqualTo("2025-12-31")
    }

}