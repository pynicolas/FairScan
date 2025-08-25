/*
 * Copyright 2025 Pierre-Yves Nicolas
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
package org.fairscan.app

import android.util.Log
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

fun enhanceCapturedImage(img: Mat): Mat {
    return if (isColoredDocument(img)) {
        Log.i("PostProcessing", "color document")
        val result = Mat()
        Core.convertScaleAbs(img, result, 1.2, 10.0)
        result
    } else {
        Log.i("PostProcessing", "grayscale document")
        val gray = multiScaleRetinex(img)
        val contrastedGray = enhanceContrastAuto(gray)
        val result = Mat()
        Imgproc.cvtColor(contrastedGray, result, Imgproc.COLOR_GRAY2BGR)
        result
    }
}

fun isColoredDocument(img: Mat, threshold: Double = 4.0): Boolean {
    val lab = Mat()
    Imgproc.cvtColor(img, lab, Imgproc.COLOR_BGR2Lab)
    val channels = ArrayList<Mat>()
    Core.split(lab, channels)

    val aStd = MatOfDouble()
    val bStd = MatOfDouble()
    Core.meanStdDev(channels[1], MatOfDouble(), aStd)
    Core.meanStdDev(channels[2], MatOfDouble(), bStd)

    val result = (aStd.toArray()[0] + bStd.toArray()[0]) / 2.0
    return result > threshold
}

private fun multiScaleRetinex(img: Mat, kernelSizes: List<Double> = listOf(30.0, 500.0)): Mat {
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
