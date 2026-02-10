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
package org.fairscan.imageprocessing

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

fun enhanceCapturedImage(img: Mat, isColored: Boolean): Mat {
    return if (isColored) {
        multiScaleRetinexOnL(img)
    } else {
        val gray = multiScaleRetinex(img)
        val contrastedGray = enhanceContrastAuto(gray)
        val result = Mat()
        Imgproc.cvtColor(contrastedGray, result, Imgproc.COLOR_GRAY2BGR)
        result
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

private fun multiScaleRetinex(img: Mat): Mat {
    val imageSize = img.size()
    val maxDim = max(imageSize.width, imageSize.height)
    val kernelSizes: List<Double> = listOf(maxDim / 50, maxDim / 3)

    // Convert to grayscale (1 channel)
    val gray = Mat()
    if (img.channels() == 4) {
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGRA2GRAY)
    } else if (img.channels() == 3) {
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY)
    } else {
        img.copyTo(gray)
    }

    val imgFloat = Mat()
    gray.convertTo(imgFloat, CvType.CV_32F)
    Core.add(imgFloat, Scalar(1.0), imgFloat) // img + 1

    val weight = 1.0 / kernelSizes.size
    val retinex = Mat.zeros(gray.size(), CvType.CV_32F)

    val logImg = Mat()
    Core.log(imgFloat, logImg)

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

    // Normalize
    val minMax = Core.minMaxLoc(retinex)
    val normalized = Mat()
    Core.subtract(retinex, Scalar(minMax.minVal), normalized)
    val scale = if (minMax.maxVal > minMax.minVal) 255.0 / (minMax.maxVal - minMax.minVal) else 1.0
    Core.multiply(normalized, Scalar(scale), normalized)

    val result = Mat()
    normalized.convertTo(result, CvType.CV_8U)

    // Cleanup
    gray.release()
    imgFloat.release()
    retinex.release()
    logImg.release()
    blur.release()
    logBlur.release()
    diff.release()
    normalized.release()

    return result
}

private fun enhanceContrastAuto(img: Mat): Mat {
    val gray = if (img.channels() == 1) img else {
        val tmp = Mat()
        Imgproc.cvtColor(img, tmp, Imgproc.COLOR_BGR2GRAY)
        tmp
    }

    // Flatten and sort pixel values
    val flat = Mat()
    gray.reshape(1, 1).convertTo(flat, CvType.CV_32F)
    val sortedVals = Mat()
    Core.sort(flat, sortedVals, Core.SORT_ASCENDING)

    val totalPixels = sortedVals.cols()
    val pLow = sortedVals.get(0, (totalPixels * 0.005).toInt())[0]
    val pHigh = sortedVals.get(0, (totalPixels * 0.80).toInt())[0]

    flat.release()
    sortedVals.release()

    val imgF = Mat()
    img.convertTo(imgF, CvType.CV_32F)
    val adjusted = Mat()
    Core.subtract(imgF, Scalar(pLow), adjusted)
    Core.multiply(adjusted, Scalar(255.0 / max((pHigh - pLow), 1.0)), adjusted)
    Core.min(adjusted, Scalar(255.0), adjusted)
    Core.max(adjusted, Scalar(0.0), adjusted)

    val result = Mat()
    adjusted.convertTo(result, CvType.CV_8U)
    imgF.release()
    adjusted.release()

    val final = Mat()
    Core.convertScaleAbs(result, final, 1.15, -25.0)
    result.release()

    return final
}
