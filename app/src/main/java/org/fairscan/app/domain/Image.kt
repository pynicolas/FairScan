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
package org.fairscan.app.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.fairscan.imageprocessing.decodeJpeg
import org.fairscan.imageprocessing.encodeJpeg
import org.fairscan.imageprocessing.packBitsMsbFirst
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class Jpeg(val bytes: ByteArray) {
    companion object {
        fun fromMat(mat: Mat, jpegQuality: Int): Jpeg = Jpeg(encodeJpeg(mat, jpegQuality))
    }
    fun toBitmap() : Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    fun toMat() : Mat = decodeJpeg(bytes)
}

// One bit per pixel, MSB first, rows padded to whole bytes, set bit means black.
class Bitonal(val width: Int, val height: Int, val bits: ByteArray)

fun packBitonal(image: Jpeg): Bitonal {
    val original = image.toMat()
    val gray = Mat()
    Imgproc.cvtColor(original, gray, Imgproc.COLOR_BGR2GRAY)
    original.release()

    val width = gray.width()
    val height = gray.height()
    val pixels = ByteArray(width * height)
    gray.get(0, 0, pixels)
    gray.release()

    return Bitonal(width, height, packBitsMsbFirst(pixels, width, height))
}

interface ImageLoader {
    suspend fun load(uri: Uri): Bitmap
}
