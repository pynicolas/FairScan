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
package org.fairscan.imageprocessing

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class BinarizationParametersTest {

    @Test
    fun sauvola_window_is_always_odd() {
        for (maxDim in 100..6000 step 7) {
            assertThat(sauvolaWindow(maxDim) % 2).isEqualTo(1)
        }
    }

    @Test
    fun sauvola_window_is_clamped_at_both_ends() {
        assertThat(sauvolaWindow(1)).isEqualTo(15)
        assertThat(sauvolaWindow(100_000)).isEqualTo(101)
    }

    @Test
    fun sauvola_window_grows_with_resolution() {
        // A4 at roughly 150, 200 and 300 dpi
        assertThat(sauvolaWindow(1189)).isEqualTo(19)
        assertThat(sauvolaWindow(1682)).isEqualTo(29)
        assertThat(sauvolaWindow(3508)).isEqualTo(59)
    }

    @Test
    fun sauvola_window_never_shrinks() {
        var previous = 0
        for (maxDim in 1..8000) {
            val window = sauvolaWindow(maxDim)
            assertThat(window).isGreaterThanOrEqualTo(previous)
            previous = window
        }
    }

    @Test
    fun despeckle_area_grows_with_the_square_of_the_resolution() {
        assertThat(despeckleMinArea(1189)).isEqualTo(2)
        assertThat(despeckleMinArea(1682)).isEqualTo(3)
        assertThat(despeckleMinArea(3508)).isEqualTo(12)
    }

    @Test
    fun despeckle_area_keeps_a_lower_bound() {
        assertThat(despeckleMinArea(1)).isEqualTo(2)
    }
}
