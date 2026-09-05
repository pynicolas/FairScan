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
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ColorMode {
    COLOR,
    GRAYSCALE,
    BLACK_AND_WHITE,
}

fun enhanceCapturedImage(img: Mat, colorMode: ColorMode): Mat {
    return when (colorMode) {
        ColorMode.COLOR -> multiScaleRetinexOnL(img)
        ColorMode.GRAYSCALE -> enhanceGrayscaleImage(img)
        ColorMode.BLACK_AND_WHITE -> binarizeDocument(img)
    }
}

fun multiScaleRetinexOnL(bgr: Mat): Mat {

    // --- 1. BGR -> Lab ---
    val lab = Mat()
    Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab)

    val labChannels = ArrayList<Mat>(3)
    Core.split(lab, labChannels)

    val l = labChannels[0] // CV_8U [0..255]

    // --- 2. Prepare L (float) ---
    val lFloat = Mat()
    l.convertTo(lFloat, CvType.CV_32F)
    Core.add(lFloat, Scalar(1.0), lFloat)

    val scaleFactor = 2.0
    val smallSize = Size(
        lFloat.cols() / scaleFactor,
        lFloat.rows() / scaleFactor
    )

    val lSmall = Mat()
    Imgproc.resize(lFloat, lSmall, smallSize, 0.0, 0.0, Imgproc.INTER_AREA)

    // --- 3. log(L) once ---
    val logLSmall = Mat()
    Core.log(lSmall, logLSmall)

    val maxDimSmall = max(smallSize.width, smallSize.height)
    val kernelSizes = listOf(
        maxDimSmall / 80.0,
        maxDimSmall / 10.0,
        maxDimSmall / 2.0,
    )

    val weight = 1.0 / kernelSizes.size
    val retinexSmall = Mat.zeros(lSmall.size(), CvType.CV_32F)

    val blurLog = Mat()
    val diff = Mat()

    for (ks in kernelSizes) {
        val k = ks.toInt().coerceAtLeast(3) or 1

        Imgproc.boxFilter(
            logLSmall,
            blurLog,
            -1,
            Size(k.toDouble(), k.toDouble())
        )

        Core.subtract(logLSmall, blurLog, diff)
        Core.addWeighted(retinexSmall, 1.0, diff, weight, 0.0, retinexSmall)
    }

    // --- 4. Normalize Retinex (relative [0..1]) ---
    val minMax = Core.minMaxLoc(retinexSmall)
    val retinexNormSmall = Mat()
    Core.subtract(retinexSmall, Scalar(minMax.minVal), retinexNormSmall)

    val range = minMax.maxVal - minMax.minVal
    if (range > 1e-6) {
        Core.multiply(retinexNormSmall, Scalar(1.0 / range), retinexNormSmall)
    }

    // --- Upscale Retinex back to full resolution ---
    val retinexNorm = Mat()
    Imgproc.resize(
        retinexNormSmall,
        retinexNorm,
        lFloat.size(),
        0.0,
        0.0,
        Imgproc.INTER_CUBIC
    )

    // --- 5. Re-center around original luminance ---
    val lOriginalFloat = Mat()
    l.convertTo(lOriginalFloat, CvType.CV_32F)

    val meanL = Core.mean(lOriginalFloat).`val`[0]
    val amplitude = 60.0

    val correctedL = Mat()
    Core.multiply(retinexNorm, Scalar(amplitude), correctedL)
    Core.add(correctedL, Scalar(meanL - amplitude / 2.0), correctedL)

    // --- 6. Blend with original L ---
    val alpha = 0.6
    Core.addWeighted(
        lOriginalFloat, 1.0 - alpha,
        correctedL, alpha,
        0.0,
        correctedL
    )

    // --- 7. Restore contrast ---
    val pLowOrig = percentileL(lOriginalFloat, 0.001)
    val pLow = percentileL(correctedL, 0.001)
    val pHigh = percentileL(correctedL, 0.995)

    val targetLow = min(pLow, pLowOrig)
    val targetHigh = 245.0
    val scale = (targetHigh - targetLow) / (pHigh - pLow + 1e-6)

    Core.subtract(correctedL, Scalar(pLow), correctedL)
    Core.multiply(correctedL, Scalar(scale), correctedL)
    Core.add(correctedL, Scalar(targetLow), correctedL)

    // --- 8. Clamp and write back ---
    Core.min(correctedL, Scalar(255.0), correctedL)
    Core.max(correctedL, Scalar(0.0), correctedL)

    correctedL.convertTo(labChannels[0], CvType.CV_8U)

    // --- 9. Lab -> BGR ---
    Core.merge(labChannels, lab)
    val result = Mat()
    Imgproc.cvtColor(lab, result, Imgproc.COLOR_Lab2BGR)

    // --- Cleanup ---
    lab.release()
    lFloat.release()
    lSmall.release()
    logLSmall.release()
    blurLog.release()
    diff.release()
    retinexSmall.release()
    retinexNormSmall.release()
    retinexNorm.release()
    lOriginalFloat.release()
    correctedL.release()
    labChannels.forEach { it.release() }

    return result
}

fun percentileL(l: Mat, p: Double): Double {
    val hist = Mat()
    Imgproc.calcHist(
        listOf(l),
        MatOfInt(0),
        Mat(),
        hist,
        MatOfInt(256),
        MatOfFloat(0f, 256f)
    )

    val total = l.total()
    var sum = 0.0
    for (i in 0 until 256) {
        sum += hist.get(i, 0)[0]
        if (sum / total >= p) {
            hist.release()
            return i.toDouble()
        }
    }
    hist.release()
    return 255.0
}

fun enhanceGrayscaleImage(img: Mat): Mat {
    val gray = flattenedGrayscale(img)
    val finalBgr = Mat()
    Imgproc.cvtColor(gray, finalBgr, Imgproc.COLOR_GRAY2BGR)
    gray.release()
    return finalBgr
}

// Steps 1 to 5 of enhanceGrayscaleImage, without the conversion back to BGR.
// Binarization reuses it for the same illumination flattening.
private fun flattenedGrayscale(img: Mat): Mat {

    // -- 1. Convert to grayscale --------
    val gray = Mat()
    when (img.channels()) {
        4    -> Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGRA2GRAY)
        3    -> Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY)
        else -> img.copyTo(gray)
    }

    // -- 2. Multi-scale Retinex ---------
    val maxDim = max(gray.cols(), gray.rows()).toDouble()

    val imgFloat = Mat()
    gray.convertTo(imgFloat, CvType.CV_32F)
    Core.add(imgFloat, Scalar(1.0), imgFloat)

    val logImg = Mat()
    Core.log(imgFloat, logImg)

    val kernelSizes = listOf(maxDim / 6, maxDim / 50)
    val weight = 1.0 / kernelSizes.size
    val retinex = Mat.zeros(gray.size(), CvType.CV_32F)
    val blur = Mat()
    val logBlur = Mat()
    val diff = Mat()

    for (kernelSize in kernelSizes) {
        Imgproc.boxFilter(imgFloat, blur, -1, Size(kernelSize, kernelSize))
        Core.add(blur, Scalar(1.0), blur)
        Core.log(blur, logBlur)
        Core.subtract(logImg, logBlur, diff)
        val diffGray = Mat()
        if (diff.channels() > 1) {
            Imgproc.cvtColor(diff, diffGray, Imgproc.COLOR_BGRA2GRAY)
        } else {
            diff.copyTo(diffGray)
        }
        Core.addWeighted(retinex, 1.0, diffGray, weight, 0.0, retinex)
        diffGray.release()
    }

    // -- 3. exp() + p1/p99 normalization ---------
    // exp() compensates for the compression of bright tones caused by
    // the Retinex log-space computation, making annotations and light
    // gray areas more visible.
    val retinexExp = Mat()
    Core.exp(retinex, retinexExp)

    val flat = Mat()
    retinexExp.reshape(1, 1).copyTo(flat)
    val sorted = Mat()
    Core.sort(flat, sorted, Core.SORT_ASCENDING)
    val n = sorted.cols()
    val pLow  = sorted.get(0, (n * 0.004).toInt())[0]
    val pHigh = sorted.get(0, (n * 0.99).toInt())[0]
    flat.release(); sorted.release()

    val normalized = Mat()
    Core.subtract(retinexExp, Scalar(pLow), normalized)
    val scale = if (pHigh > pLow) 255.0 / (pHigh - pLow) else 1.0
    Core.multiply(normalized, Scalar(scale), normalized)
    Core.min(normalized, Scalar(255.0), normalized)
    Core.max(normalized, Scalar(0.0), normalized)
    retinexExp.release()

    val result8u = Mat()
    normalized.convertTo(result8u, CvType.CV_8U)
    normalized.release()

    // -- 4. Stretch toward white --------
    // Find the histogram mode in [180..255] as an estimate of the background level,
    // then stretch so that level maps to 255.
    // If modeVal >= 254, Retinex has over-amplified the image (typically happens
    // when the document contains large dark areas). In that case, fall back to
    // a simple normalization of the original grayscale image.
    val hist = Mat()
    Imgproc.calcHist(listOf(result8u), MatOfInt(0), Mat(), hist,
        MatOfInt(256), MatOfFloat(0f, 256f))

    var modeVal = 220; var modeCount = 0.0
    for (i in 180 until 256) {
        val c = hist.get(i, 0)[0]
        if (c > modeCount) { modeCount = c; modeVal = i }
    }
    hist.release()

    val stretched8u = Mat()

    if (modeVal >= 254) {
        val grayF = Mat()
        gray.convertTo(grayF, CvType.CV_32F)
        val grayFlat = Mat()
        grayF.reshape(1, 1).copyTo(grayFlat)
        val graySorted = Mat()
        Core.sort(grayFlat, graySorted, Core.SORT_ASCENDING)
        val gN = graySorted.cols()
        val gLow  = graySorted.get(0, (gN * 0.01).toInt())[0]
        val gHigh = graySorted.get(0, (gN * 0.99).toInt())[0]
        grayFlat.release(); graySorted.release()
        Core.subtract(grayF, Scalar(gLow), grayF)
        Core.multiply(grayF, Scalar(255.0 / (gHigh - gLow + 1e-6)), grayF)
        Core.min(grayF, Scalar(255.0), grayF)
        Core.max(grayF, Scalar(0.0), grayF)
        grayF.convertTo(stretched8u, CvType.CV_8U)
        grayF.release()
    } else {
        val stretchedF = Mat()
        result8u.convertTo(stretchedF, CvType.CV_32F)
        Core.multiply(stretchedF, Scalar(255.0 / modeVal), stretchedF)
        Core.min(stretchedF, Scalar(255.0), stretchedF)
        stretchedF.convertTo(stretched8u, CvType.CV_8U)
        stretchedF.release()
    }

    // -- 5. Bilateral denoising ---------
    // Smooths background texture and fine grain amplified by exp() and stretch,
    // while preserving sharp edges (text, lines, annotations).
    val denoised = Mat()
    Imgproc.bilateralFilter(stretched8u, denoised, 9, 20.0, 10.0)

    // -- Cleanup -----------
    gray.release(); imgFloat.release(); logImg.release()
    blur.release(); logBlur.release(); diff.release()
    retinex.release(); result8u.release()
    stretched8u.release()

    return denoised
}

private const val SAUVOLA_K = 0.25
private const val SAUVOLA_R = 128.0

// Window and deviation below which an area counts as a flat fill rather than texture.
private const val FILL_WINDOW = 9.0
private const val FILL_DEVIATION = 12.0

// Step 4 of the grayscale pipeline stretches the page background to white.
private const val FILL_LEVEL = 155.0

// How much larger a hole may be inside a fill before it counts as content rather than glare.
private const val FILL_HOLE_FACTOR = 5

// Returns BGR containing only 0 and 255, like the other color modes, so that storage and
// preview stay unchanged. The export path packs it into one bit per pixel.
fun binarizeDocument(img: Mat, upscaleTo: Long = 0L): Mat {
    // Flatten the illumination at the captured resolution. Interpolated pixels carry no extra
    // information for that step, only for where the threshold puts an edge.
    val flattened = flattenedGrayscale(img)
    val gray = upscaleToPixels(flattened, upscaleTo)
    flattened.release()
    val window = sauvolaWindow(max(gray.cols(), gray.rows()))

    val src = Mat()
    gray.convertTo(src, CvType.CV_32F)
    gray.release()

    val binary = sauvolaThreshold(src, window)
    val fill = flatFill(src, window)
    src.release()

    // A local threshold has no reference point inside a flat fill of color, so the fill reads
    // as background and comes out as an outline of itself.
    Core.subtract(binary, fill, binary)
    despeckle(binary, fill, window)
    fill.release()

    val bgr = Mat()
    Imgproc.cvtColor(binary, bgr, Imgproc.COLOR_GRAY2BGR)
    binary.release()
    return bgr
}

private fun upscaleToPixels(img: Mat, targetPixels: Long): Mat {
    val pixels = img.width().toLong() * img.height()
    if (targetPixels <= pixels) return img.clone()
    val scale = sqrt(targetPixels.toDouble() / pixels)
    val out = Mat()
    Imgproc.resize(img, out, Size(img.width() * scale, img.height() * scale),
        0.0, 0.0, Imgproc.INTER_CUBIC)
    return out
}

// Sauvola local thresholding: t = mean * (1 + k * (stdDev / r - 1))
private fun sauvolaThreshold(src: Mat, window: Int): Mat {
    val windowSize = Size(window.toDouble(), window.toDouble())

    val mean = Mat()
    Imgproc.boxFilter(src, mean, CvType.CV_32F, windowSize)

    val squares = Mat()
    Core.multiply(src, src, squares)
    val deviation = Mat()
    Imgproc.boxFilter(squares, deviation, CvType.CV_32F, windowSize)
    squares.release()

    val meanSquared = Mat()
    Core.multiply(mean, mean, meanSquared)
    Core.subtract(deviation, meanSquared, deviation)
    meanSquared.release()
    Core.max(deviation, Scalar(0.0), deviation)
    Core.sqrt(deviation, deviation)

    Core.multiply(deviation, Scalar(SAUVOLA_K / SAUVOLA_R), deviation)
    Core.add(deviation, Scalar(1.0 - SAUVOLA_K), deviation)
    val threshold = Mat()
    Core.multiply(mean, deviation, threshold)
    mean.release(); deviation.release()

    val binary = Mat()
    Core.compare(src, threshold, binary, Core.CMP_GT)
    threshold.release()

    return binary
}

// Dark pixels belonging to a flat fill: a smooth dark spot seeds the fill, the seed grows over
// the area one local window covers, and the result is clipped back to the dark pixels. Texture
// produces almost no seeds, so photographs stay on the local threshold.
private fun flatFill(src: Mat, window: Int): Mat {
    val dark = Mat()
    Core.compare(src, Scalar(FILL_LEVEL), dark, Core.CMP_LT)

    val deviation = localDeviation(src, FILL_WINDOW)
    val seeds = Mat()
    Core.compare(deviation, Scalar(FILL_DEVIATION), seeds, Core.CMP_LT)
    deviation.release()
    Core.bitwise_and(seeds, dark, seeds)

    // Half the local window is enough: that is how far the threshold is disturbed around a
    // bright feature sitting on the fill.
    val reach = (window / 2).coerceAtLeast(3).toDouble()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(reach, reach))
    val fill = Mat()
    Imgproc.dilate(seeds, fill, kernel)
    seeds.release(); kernel.release()

    Core.bitwise_and(fill, dark, fill)
    dark.release()
    return fill
}

// Standard deviation of src over a square window, as CV_32F.
private fun localDeviation(src: Mat, window: Double): Mat {
    val windowSize = Size(window, window)
    val mean = Mat()
    Imgproc.boxFilter(src, mean, CvType.CV_32F, windowSize)
    val squares = Mat()
    Core.multiply(src, src, squares)
    val deviation = Mat()
    Imgproc.boxFilter(squares, deviation, CvType.CV_32F, windowSize)
    squares.release()
    Core.multiply(mean, mean, mean)
    Core.subtract(deviation, mean, deviation)
    mean.release()
    Core.max(deviation, Scalar(0.0), deviation)
    Core.sqrt(deviation, deviation)
    return deviation
}

// About two to three times the cap height of body text at any of the export resolutions.
internal fun sauvolaWindow(maxDim: Int): Int = (maxDim / 60).coerceIn(15, 101) or 1

// Grows with the square of the resolution, anchored so that at 300 dpi anything up to 3x3 is
// removed while a full stop, about 35 px, survives.
internal fun despeckleMinArea(maxDim: Int): Int {
    val scale = maxDim / 3508.0
    return max(2, (12.0 * scale * scale).roundToInt())
}

// Removes specks of both kinds: ink on paper, and the holes that glare punches into a fill.
private fun despeckle(binary: Mat, fill: Mat, window: Int) {
    val minArea = despeckleMinArea(max(binary.cols(), binary.rows()))
    removeSpecks(binary, minArea, ink = true)
    removeSpecks(binary, minArea, ink = false)

    // Glare and uneven printing punch holes into a filled area that are far bigger than the
    // tiny ones above, and about the size of a letter counter. A counter never sits inside a
    // fill though, so within one the size limit can be raised without eating any text.
    val reach = (window / 4).coerceAtLeast(3).toDouble()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(reach, reach))
    val inside = Mat()
    Imgproc.dilate(fill, inside, kernel)
    kernel.release()
    removeSpecks(binary, minArea * FILL_HOLE_FACTOR, ink = false, within = inside)
    inside.release()
}

// Drops connected areas below minArea, optionally only those centred in `within`. Only the
// bounding box of each speck is touched, never the whole image.
private fun removeSpecks(binary: Mat, minArea: Int, ink: Boolean, within: Mat? = null) {
    val subject = Mat()
    if (ink) Core.bitwise_not(binary, subject) else binary.copyTo(subject)

    val labels = Mat()
    val stats = Mat()
    val centroids = Mat()
    val count = Imgproc.connectedComponentsWithStats(
        subject, labels, stats, centroids, 8, CvType.CV_32S)
    subject.release()

    if (count > 1) {
        val statsData = IntArray(count * 5)
        stats.get(0, 0, statsData)
        val centres = DoubleArray(count * 2)
        centroids.get(0, 0, centres)
        val replacement = Scalar(if (ink) 255.0 else 0.0)

        for (label in 1 until count) {
            val offset = label * 5
            if (statsData[offset + Imgproc.CC_STAT_AREA] >= minArea) continue
            if (within != null && !isSet(within, centres[label * 2], centres[label * 2 + 1]))
                continue
            val box = Rect(
                statsData[offset + Imgproc.CC_STAT_LEFT],
                statsData[offset + Imgproc.CC_STAT_TOP],
                statsData[offset + Imgproc.CC_STAT_WIDTH],
                statsData[offset + Imgproc.CC_STAT_HEIGHT],
            )
            val labelBox = labels.submat(box)
            val speck = Mat()
            Core.compare(labelBox, Scalar(label.toDouble()), speck, Core.CMP_EQ)
            val target = binary.submat(box)
            target.setTo(replacement, speck)
            labelBox.release(); speck.release(); target.release()
        }
    }

    labels.release(); stats.release(); centroids.release()
}

private fun isSet(mask: Mat, x: Double, y: Double): Boolean {
    val col = x.toInt().coerceIn(0, mask.cols() - 1)
    val row = y.toInt().coerceIn(0, mask.rows() - 1)
    return mask.get(row, col)[0] != 0.0
}
