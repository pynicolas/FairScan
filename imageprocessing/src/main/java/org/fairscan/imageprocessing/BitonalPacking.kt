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

private const val BITONAL_THRESHOLD = 128

private fun bytesPerRow(width: Int): Int = (width + 7) / 8

// One bit per pixel, MSB first, each row padded to whole bytes. A set bit means black, which
// is what the CCITT fax encoder expects when /BlackIs1 is left out.
fun packBitsMsbFirst(gray: ByteArray, width: Int, height: Int): ByteArray {
    val stride = bytesPerRow(width)
    val packed = ByteArray(stride * height)
    for (y in 0 until height) {
        val sourceRow = y * width
        val targetRow = y * stride
        for (x in 0 until width) {
            if ((gray[sourceRow + x].toInt() and 0xFF) < BITONAL_THRESHOLD) {
                val index = targetRow + (x shr 3)
                packed[index] = (packed[index].toInt() or (0x80 ushr (x and 7))).toByte()
            }
        }
    }
    return packed
}

// Inverse of packBitsMsbFirst.
internal fun unpackBitsMsbFirst(packed: ByteArray, width: Int, height: Int): ByteArray {
    val stride = bytesPerRow(width)
    val gray = ByteArray(width * height)
    for (y in 0 until height) {
        val sourceRow = y * stride
        val targetRow = y * width
        for (x in 0 until width) {
            val bit = (packed[sourceRow + (x shr 3)].toInt() shr (7 - (x and 7))) and 1
            gray[targetRow + x] = if (bit == 1) 0 else 255.toByte()
        }
    }
    return gray
}
