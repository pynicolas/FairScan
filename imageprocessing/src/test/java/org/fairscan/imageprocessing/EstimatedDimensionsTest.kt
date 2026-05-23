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
import org.fairscan.imageprocessing.EstimatedDimensions.Physical
import org.fairscan.imageprocessing.EstimatedDimensions.Ratio
import org.junit.Test

class EstimatedDimensionsTest {

    @Test fun `ratio is returned unchanged`() {
        val input = Ratio(212.0, 300.0)
        assertThat(input.snapToStandardFormat()).isEqualTo(input)
    }

    @Test fun `A4-sized physical dims snap to exact A4`() {
        val input = Physical(212.0, 300.0)
        assertThat(input.snapToStandardFormat()).isEqualTo(PaperFormats.A4)
    }

    @Test fun `A3-sized physical dims snap to exact A3`() {
        val input = Physical(297.0, 420.0)
        assertThat(input.snapToStandardFormat()).isEqualTo(PaperFormats.A3)
    }

    @Test fun `A5-sized physical dims snap to exact A5`() {
        val input = Physical(149.0, 211.0)
        assertThat(input.snapToStandardFormat()).isEqualTo(PaperFormats.A5)
    }

    @Test fun `landscape A4 snaps to landscape A4`() {
        val input = Physical(300.0, 212.0)
        assertThat(input.snapToStandardFormat()).isEqualTo(Physical(297.0, 210.0))
    }

    @Test fun `Letter-sized dims snap to exact Letter`() {
        val input = Physical(218.0, 281.0)
        assertThat(input.snapToStandardFormat()).isEqualTo(PaperFormats.Letter)
    }

    @Test fun `dims far from any standard format are unchanged`() {
        val input = Physical(150.0, 400.0)
        assertThat(input.snapToStandardFormat()).isEqualTo(input)
    }

    @Test fun `aspect ratio`() {
        assertThat(Physical(100.0, 150.0).aspectRatio).isEqualTo(1.5)
        assertThat(Ratio(100.0, 150.0).aspectRatio).isEqualTo(1.5)
    }
}
