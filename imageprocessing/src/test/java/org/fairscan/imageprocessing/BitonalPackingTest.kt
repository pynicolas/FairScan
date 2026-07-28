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
import kotlin.random.Random

private const val BLACK: Byte = 0
private const val WHITE = 255.toByte()

class BitonalPackingTest {

    @Test
    fun single_black_pixel() {
        assertThat(packBitsMsbFirst(byteArrayOf(BLACK), 1, 1)).containsExactly(0x80.toByte())
    }

    @Test
    fun single_white_pixel() {
        assertThat(packBitsMsbFirst(byteArrayOf(WHITE), 1, 1)).containsExactly(0x00)
    }

    @Test
    fun most_significant_bit_is_the_leftmost_pixel() {
        val row = byteArrayOf(BLACK, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, BLACK)
        assertThat(packBitsMsbFirst(row, 8, 1)).containsExactly(0x81.toByte())
    }

    @Test
    fun rows_are_padded_to_whole_bytes() {
        // 9 pixels: 8 white, then one black, so the ninth bit is the MSB of the second byte
        val row = ByteArray(9) { if (it == 8) BLACK else WHITE }
        val packed = packBitsMsbFirst(row, 9, 1)
        assertThat(packed).hasSize(2)
        assertThat(packed).containsExactly(0x00, 0x80.toByte())
    }

    @Test
    fun rows_do_not_bleed_into_each_other() {
        val pixels = ByteArray(2 * 9) { if (it < 9) WHITE else BLACK }
        val packed = packBitsMsbFirst(pixels, 9, 2)
        assertThat(packed).containsExactly(0x00, 0x00, 0xFF.toByte(), 0x80.toByte())
    }

    @Test
    fun packed_size_only_depends_on_dimensions() {
        assertThat(packBitsMsbFirst(ByteArray(37 * 23), 37, 23)).hasSize(5 * 23)
    }

    @Test
    fun gray_values_are_thresholded_at_the_midpoint() {
        val row = byteArrayOf(127, 128.toByte())
        assertThat(packBitsMsbFirst(row, 2, 1)).containsExactly(0x80.toByte())
    }

    @Test
    fun round_trip() {
        val random = Random(42)
        val width = 37
        val height = 23
        val pixels = ByteArray(width * height) { if (random.nextBoolean()) BLACK else WHITE }
        val packed = packBitsMsbFirst(pixels, width, height)
        assertThat(unpackBitsMsbFirst(packed, width, height)).isEqualTo(pixels)
    }
}
