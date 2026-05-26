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

class OcrTest {

    @Test
    fun `top left in image becomes bottom left in PDF`() {
        val converter = OcrCoordinateConverter(
            imageWidth = 1000, imageHeight = 2000,
            pageWidth = 500f, pageHeight = 1000f
        )
        val result = converter.convert(ImageRect(0, 0, 100, 200))
        assertThat(result.x).isEqualTo(0f)
        assertThat(result.y).isEqualTo(1000f - 100f)
        assertThat(result.width).isEqualTo(50f)
        assertThat(result.height).isEqualTo(100f)
    }

    @Test
    fun `bottom right in image becomes top right in PDF`() {
        val converter = OcrCoordinateConverter(
            imageWidth = 1000, imageHeight = 2000,
            pageWidth = 500f, pageHeight = 1000f
        )
        val result = converter.convert(ImageRect(900, 1800, 1000, 2000))
        assertThat(result.x).isEqualTo(450f)
        assertThat(result.y).isEqualTo(0f)
        assertThat(result.width).isEqualTo(50f)
        assertThat(result.height).isEqualTo(100f)
    }

}
