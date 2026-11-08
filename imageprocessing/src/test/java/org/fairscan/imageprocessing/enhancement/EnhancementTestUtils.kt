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
package org.fairscan.imageprocessing.enhancement

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.random.Random

/**
 * Builds a fake photo of a page.
 * Default size ~150 dpi on A4: the tested code makes assumptions about the image size
 * so very small images would not behave like real captures.
 */
class SyntheticPage(val width: Int = 1200, val height: Int = 1700) {

    private val canvas = Mat(height, width, CvType.CV_8UC3,
        Scalar(PAPER_LEVEL.toDouble(), PAPER_LEVEL.toDouble(), PAPER_LEVEL.toDouble()))
    private var lightFalloff = 0.0

    fun lightFalloff(fraction: Double) = apply { lightFalloff = fraction }

    /** Gray rectangle (0 = black, 255 = white). */
    fun rect(rect: Rect, level: Int) =
        rect(rect, Scalar(level.toDouble(), level.toDouble(), level.toDouble()))

    /** Colored rectangle, in BGR. */
    fun rect(rect: Rect, bgr: Scalar) = apply {
        // Imgproc.rectangle includes both corners: the last pixel is x + width - 1
        Imgproc.rectangle(
            canvas,
            Point(rect.x.toDouble(), rect.y.toDouble()),
            Point((rect.x + rect.width - 1).toDouble(), (rect.y + rect.height - 1).toDouble()),
            bgr, Imgproc.FILLED)
    }

    /** Light and noise are the same on the 3 channels: they change the level, not the hue. */
    fun build(noiseSigma: Double = 0.0, seed: Int = 42): Mat {
        val image = Mat()
        canvas.convertTo(image, CvType.CV_32FC3)

        if (lightFalloff > 0) {
            val ramp = Mat(1, width, CvType.CV_32F)
            ramp.put(0, 0, FloatArray(width) { x -> (1.0 - lightFalloff * x / width).toFloat() })
            val shading = Mat()
            Core.repeat(ramp, height, 1, shading)
            val shading3 = Mat()
            Core.merge(listOf(shading, shading, shading), shading3)
            Core.multiply(image, shading3, image)
            ramp.release(); shading.release(); shading3.release()
        }

        if (noiseSigma > 0) {
            val random = Random(seed)
            val noise = Mat(height, width, CvType.CV_32F)
            noise.put(0, 0, FloatArray(width * height) { (gaussian(random) * noiseSigma).toFloat() })
            val noise3 = Mat()
            Core.merge(listOf(noise, noise, noise), noise3)
            Core.add(image, noise3, image)
            noise.release(); noise3.release()
        }

        val bgr = Mat()
        image.convertTo(bgr, CvType.CV_8UC3) // saturates to 0..255
        image.release()
        return bgr
    }

    // Box-Muller, to stay independent of OpenCV's random generator
    private fun gaussian(random: Random): Double {
        val u1 = 1.0 - random.nextDouble()
        val u2 = random.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)
    }

    companion object {
        const val PAPER_LEVEL = 225
    }
}

/** Single-channel 8-bit view of the image, whatever the number of channels. */
private fun toGray(src: Mat): Mat {
    val gray = Mat()
    when (src.channels()) {
        1 -> src.copyTo(gray)
        4 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGRA2GRAY)
        else -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
    }
    return gray
}

/** Share of black pixels of a binarized image in the given area. */
fun Mat.blackRatio(area: Rect): Double {
    val region = this.submat(area)
    val gray = toGray(region)
    region.release()
    val white = Core.countNonZero(gray)
    val total = gray.rows() * gray.cols()
    gray.release()
    return 1.0 - white.toDouble() / total
}

/** True if every pixel of every channel is 0 or 255. */
fun Mat.isStrictlyBinary(): Boolean {
    // reshape(1) makes all channels look like one: the check stays strict on each of them
    val flat = this.reshape(1)
    val mask = Mat()
    Core.inRange(flat, Scalar(1.0), Scalar(254.0), mask)
    val intermediate = Core.countNonZero(mask)
    mask.release()
    return intermediate == 0
}

fun Rect.inset(margin: Int) = Rect(x + margin, y + margin, width - 2 * margin, height - 2 * margin)

/** Writes input and output side by side in build/test-images, to look at when a test fails. */
fun saveDebugImage(name: String, input: Mat, output: Mat) {
    val left = toBgr8(input)
    val right = toBgr8(output)
    if (right.rows() != left.rows()) {
        val scale = left.rows().toDouble() / right.rows()
        val resized = Mat()
        Imgproc.resize(right, resized, Size(), scale, scale, Imgproc.INTER_NEAREST)
        right.release()
        return writeSideBySide(name, left, resized)
    }
    writeSideBySide(name, left, right)
}

private fun writeSideBySide(name: String, left: Mat, right: Mat) {
    val both = Mat()
    Core.hconcat(listOf(left, right), both)
    val dir = File("build/test-images").apply { mkdirs() }
    Imgcodecs.imwrite(File(dir, "$name.png").path, both)
    both.release(); left.release(); right.release()
}

private fun toBgr8(src: Mat): Mat {
    val eight = Mat()
    if (src.depth() == CvType.CV_8U) src.copyTo(eight) else src.convertTo(eight, CvType.CV_8U)
    val bgr = Mat()
    when (eight.channels()) {
        1 -> Imgproc.cvtColor(eight, bgr, Imgproc.COLOR_GRAY2BGR)
        4 -> Imgproc.cvtColor(eight, bgr, Imgproc.COLOR_BGRA2BGR)
        else -> eight.copyTo(bgr)
    }
    eight.release()
    return bgr
}
