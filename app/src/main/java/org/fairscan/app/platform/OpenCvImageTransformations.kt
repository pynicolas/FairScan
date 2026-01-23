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
package org.fairscan.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import org.fairscan.app.data.ImageTransformations
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import kotlin.math.min

class OpenCvTransformations : ImageTransformations {
    override fun rotate(
        inputFile: File,
        outputFile: File,
        rotationDegrees: Int,
        jpegQuality: Int
    ) {
        val src = Imgcodecs.imread(inputFile.absolutePath)
        require(!src.empty()) { "Could not load image from ${inputFile.absolutePath}" }

        val dst = org.fairscan.imageprocessing.rotate(src, rotationDegrees)

        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality)
        if (!Imgcodecs.imwrite(outputFile.absolutePath, dst, params)) {
            throw RuntimeException("Could not write image to ${outputFile.absolutePath}")
        }

        params.release()
        src.release()
        dst.release()
    }

    override fun resize(inputFile: File, outputFile: File, maxSize: Int) {
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
        val ratio = min(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
        val newW = (bitmap.width * ratio).toInt()
        val newH = (bitmap.height * ratio).toInt()
        val scaled = bitmap.scale(newW, newH)
        outputFile.outputStream().use {
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }
    }
}
