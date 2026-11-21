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

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.IOException
import kotlin.math.sqrt

fun resizeForMaxPixels(img: Mat, maxPixels: Double, interpolation: Int = Imgproc.INTER_AREA): Mat {
    val origPixels = img.width() * img.height()
    if (origPixels <= maxPixels) {
        return img.clone()
    }
    val scale = sqrt(maxPixels / origPixels)
    val size = Size(img.width() * scale, img.height() * scale)
    val resizedImg = Mat()
    Imgproc.resize(img, resizedImg, size, 0.0, 0.0, interpolation)
    return resizedImg
}

enum class ImageEncoding(val fileNameExtension: String) {
    JPEG(".jpg"), PNG(".png")
}

fun encodeJpeg(mat: Mat, jpegQuality: Int): ByteArray {
    val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality.coerceIn(0, 100))
    return encode(mat, params, ImageEncoding.JPEG)
}

fun encodePng(mat: Mat): ByteArray {
    val params = MatOfInt(Imgcodecs.IMWRITE_PNG_COMPRESSION, 1)
    return encode(mat, params, ImageEncoding.PNG)
}

private fun encode(mat: Mat, params: MatOfInt, encoding: ImageEncoding): ByteArray {
    val encoded = MatOfByte()
    val ok = Imgcodecs.imencode(encoding.fileNameExtension, mat, encoded, params)
    params.release()

    if (!ok) {
        encoded.release()
        throw IOException("Failed to encode $encoding")
    }

    val result = encoded.toArray()
    encoded.release()
    return result
}

fun decodeJpegOrPng(bytes: ByteArray): Mat {
    val src = MatOfByte(*bytes)
    val decoded = Imgcodecs.imdecode(src, Imgcodecs.IMREAD_COLOR)
    src.release()
    if (decoded.empty()) {
        decoded.release()
        throw IllegalStateException("Failed to decode JPEG/PNG")
    }
    return decoded
}

/**
 * Smallest 8-bit level such that a share `p` (0..1) of the pixels is at or below it.
 * Only the pixels selected by `mask` are counted, if there is one.
 * Without a mask, values outside [0, 256[ are ignored by the histogram but still counted in the total.
 */
fun percentile(channel: Mat, p: Double, mask: Mat = Mat()): Double =
    Mat().use { hist ->
        Imgproc.calcHist(
            listOf(channel), MatOfInt(0), mask, hist, MatOfInt(256), MatOfFloat(0f, 256f)
        )
        val total =
            if (mask.empty())
                channel.total().toDouble()
            else
                Core.countNonZero(mask).toDouble()

        var sum = 0.0
        for (i in 0 until 256) {
            sum += hist.get(i, 0)[0]
            if (sum / total >= p) return@use i.toDouble()
        }
        255.0
    }

inline fun <R> Mat.use(block: (Mat) -> R): R =
    try {
        block(this)
    } finally {
        release()
    }

// Chroma of the Lab image from its a and b channels (8-bit, centered on 128), as CV_32F
// https://en.wikipedia.org/wiki/CIELAB_color_space
fun labChroma(a: Mat, b: Mat): Mat {
    val aShifted = Mat()
    val bShifted = Mat()
    a.convertTo(aShifted, CvType.CV_32F, 1.0, -128.0)
    b.convertTo(bShifted, CvType.CV_32F, 1.0, -128.0)
    val chroma = Mat()
    Core.magnitude(aShifted, bShifted, chroma)
    aShifted.release()
    bShifted.release()
    return chroma
}
