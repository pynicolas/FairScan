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
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class WhiteBalanceAnalysis(
    val paperColor: PaperColor,
    val gains: ChannelGains,
    val strength: Double,
    val topChroma: Double,
)

fun analyzeWhiteBalance(img: Mat): WhiteBalanceAnalysis {
    val small = smallBgr(img)
    val paperColor = estimateBrightPercentile(small)
    val topChroma = topChroma(small, gainsFor(paperColor))
    val strength = ramp(topChroma)
    small.release()
    return WhiteBalanceAnalysis(
        paperColor = paperColor,
        gains = gainsFor(paperColor, strength = strength),
        strength = strength,
        topChroma = topChroma,
    )
}

// Estimated color of the blank paper, on the 0..255 scale of the input image
data class PaperColor(val b: Double, val g: Double, val r: Double)

// Multiplicative factor per channel
data class ChannelGains(val b: Double, val g: Double, val r: Double) {
    val isIdentity: Boolean
        get() = abs(b - 1.0) < 0.005 && abs(g - 1.0) < 0.005 && abs(r - 1.0) < 0.005

    companion object {
        val IDENTITY = ChannelGains(1.0, 1.0, 1.0)
    }

    // Multiplies each channel by its gain. Expects a CV_8UC3 BGR image, returns a new Mat
    fun applyTo(img: Mat): Mat {
        if (isIdentity || img.channels() != 3) return img.clone()

        val data = ByteArray(256 * 3)
        for (i in 0 until 256) {
            data[i * 3] = (i * b).roundToInt().coerceIn(0, 255).toByte()
            data[i * 3 + 1] = (i * g).roundToInt().coerceIn(0, 255).toByte()
            data[i * 3 + 2] = (i * r).roundToInt().coerceIn(0, 255).toByte()
        }
        val lut = Mat(1, 256, CvType.CV_8UC3)
        lut.put(0, 0, data)
        val out = Mat()
        Core.LUT(img, lut, out)
        lut.release()
        return out
    }

    val maxChange: Double get() = maxOf(abs(b - 1.0), abs(g - 1.0), abs(r - 1.0))
}

// Share of the brightest pixels (by gray level) considered to be "paper"
private const val BRIGHT_SHARE = 0.15

// Median color of the brightest pixels of the whole image
private fun estimateBrightPercentile(small: Mat): PaperColor {
    val gray = Mat()
    Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
    val mask = brightestMask(gray, BRIGHT_SHARE)
    val result = medianPaperColor(small, mask)
    gray.release(); mask.release()
    return result
}

private const val TOP_CHROMA_FULL = 4.0
private const val TOP_CHROMA_NONE = 6.0
// Share of the brightest pixels looked at by the top-chroma metric.
private const val TOP_SHARE = 0.08

// Applies the gains and measures the chroma of the brightest TOP_SHARE of them
// https://en.wikipedia.org/wiki/CIELAB_color_space
private fun topChroma(small: Mat, gains: ChannelGains): Double {
    val corrected = gains.applyTo(small)
    val lab = Mat()
    Imgproc.cvtColor(corrected, lab, Imgproc.COLOR_BGR2Lab)
    corrected.release()
    val channels = ArrayList<Mat>(3)
    Core.split(lab, channels)
    lab.release()

    val top = brightestMask(channels[0], TOP_SHARE)
    val a = Mat()
    val b = Mat()
    channels[1].convertTo(a, CvType.CV_32F, 1.0, -128.0)
    channels[2].convertTo(b, CvType.CV_32F, 1.0, -128.0)
    val chroma = Mat()
    Core.magnitude(a, b, chroma)
    val mean = Core.mean(chroma, top).`val`[0]

    listOf(top, a, b, chroma).forEach { it.release() }
    channels.forEach { it.release() }
    return mean
}

private fun brightestMask(l: Mat, share: Double): Mat {
    val threshold = percentileL(l, 1.0 - share)
    val mask = Mat()
    Core.compare(l, Scalar(threshold), mask, Core.CMP_GE)
    return mask
}

private fun ramp(topChroma: Double): Double {
    val t = ((TOP_CHROMA_NONE - topChroma) / (TOP_CHROMA_NONE - TOP_CHROMA_FULL)).coerceIn(0.0, 1.0)
    // https://en.wikipedia.org/wiki/Smoothstep
    return t * t * (3.0 - 2.0 * t)
}

/**
 * Gains that bring the paper to a neutral color while keeping its average level, with
 * `strength` (0..1) of the correction applied.
 *
 * Each gain is clamped to [minGain, maxGain]. The clamp matters for the decision: a cast
 * stronger than that is unlikely to be only light, and the paper color of a colorful cover
 * is then left partly colored, which topChroma detects.
 */
private fun gainsFor(
    paper: PaperColor,
    strength: Double = 1.0,
    minGain: Double = 0.75,
    maxGain: Double = 1.33,
): ChannelGains {
    val target = (paper.b + paper.g + paper.r) / 3.0
    if (target < 40.0) return ChannelGains.IDENTITY // too dark to say anything

    fun gain(channel: Double) =
        1.0 + ((target / max(channel, 1.0)).coerceIn(minGain, maxGain) - 1.0) * strength
    return ChannelGains(gain(paper.b), gain(paper.g), gain(paper.r))
}

// Estimation runs on a reduced copy
private const val ESTIMATION_PIXELS = 250_000.0

private fun smallBgr(img: Mat): Mat {
    val bgr = Mat()
    when (img.channels()) {
        4 -> Imgproc.cvtColor(img, bgr, Imgproc.COLOR_BGRA2BGR)
        3 -> img.copyTo(bgr)
        else -> Imgproc.cvtColor(img, bgr, Imgproc.COLOR_GRAY2BGR)
    }
    val small = resizeForMaxPixels(bgr, ESTIMATION_PIXELS)
    bgr.release()
    return small
}

// Median of each channel over the pixels selected by `mask` from a histogram
private fun medianPaperColor(small: Mat, mask: Mat): PaperColor {
    val channels = ArrayList<Mat>(3)
    Core.split(small, channels)
    val medians = channels.map { histogramMedian(it, mask) }
    channels.forEach { it.release() }
    return PaperColor(medians[0], medians[1], medians[2])
}

private fun histogramMedian(channel: Mat, mask: Mat): Double {
    val hist = Mat()
    Imgproc.calcHist(listOf(channel), MatOfInt(0), mask, hist, MatOfInt(256), MatOfFloat(0f, 256f))
    var total = 0.0
    for (i in 0 until 256) total += hist.get(i, 0)[0]

    var sum = 0.0
    var median = 255
    for (i in 0 until 256) {
        sum += hist.get(i, 0)[0]
        if (sum >= total / 2.0) {
            median = i
            break
        }
    }
    hist.release()
    return median.toDouble()
}
