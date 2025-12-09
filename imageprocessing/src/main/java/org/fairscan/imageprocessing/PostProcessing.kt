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
package org.fairscan.imageprocessing

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.roundToInt

fun enhanceCapturedImage(img: Mat, isColored: Boolean): Mat {
    return if (isColored) {
        val result = Mat()
        Core.convertScaleAbs(img, result, 1.2, 10.0)
        result
    } else {
        val gray = multiScaleRetinex(img)
        val contrastedGray = enhanceContrastAuto(gray)
        val result = Mat()
        Imgproc.cvtColor(contrastedGray, result, Imgproc.COLOR_GRAY2BGR)
        result
    }
}

fun isColoredDocument(
    img: Mat,
    mask: Mask,
    quad: Quad,
    chromaThreshold: Double = 17.5,
    proportionThreshold: Double = 0.0003,
    luminanceMin: Double = 40.0,
    luminanceMax: Double = 180.0
): Boolean {

    // 1) Compute doc mask (mask ∩ quad)
    val imgSize = Size(img.width().toDouble(), img.height().toDouble())
    val resizedMask = Mat()
    Imgproc.resize(mask.toMat(), resizedMask, imgSize, 0.0, 0.0, Imgproc.INTER_AREA)
    val erodedMask = erodeBorder(resizedMask, quad)
    val docMask = quadMaskIntersection(erodedMask, quad)
    erodedMask.release()
    resizedMask.release()

    // 2) Apply white balance only inside document
    val whiteBalanced = applyGrayWorldToDocument(img, docMask)

    // 3) Convert to Lab, see https://en.wikipedia.org/wiki/CIELAB_color_space
    val lab = Mat()
    Imgproc.cvtColor(whiteBalanced, lab, Imgproc.COLOR_BGR2Lab)

    // 4) Split Lab
    val channels = ArrayList<Mat>()
    Core.split(lab, channels)
    val luminance = channels[0]
    val a = channels[1]
    val b = channels[2]

    // 5) Compute chroma
    val aFloat = Mat()
    val bFloat = Mat()
    a.convertTo(aFloat, CvType.CV_32F)
    b.convertTo(bFloat, CvType.CV_32F)

    val aShifted = Mat()
    val bShifted = Mat()
    Core.subtract(aFloat, Scalar(128.0), aShifted)
    Core.subtract(bFloat, Scalar(128.0), bShifted)

    val chroma = Mat()
    Core.magnitude(aShifted, bShifted, chroma)

    val colorMask = Mat()
    Imgproc.threshold(chroma, colorMask, chromaThreshold, 255.0, Imgproc.THRESH_BINARY)
    colorMask.convertTo(colorMask, CvType.CV_8U)

    // 6) Create luminance mask L ∈ [luminanceMin, luminanceMax]
    val luminanceMask = Mat()
    Core.inRange(luminance, Scalar(luminanceMin), Scalar(luminanceMax), luminanceMask)

    // 7) Combine colorMask & LMask & docMask
    val docMask8 = Mat()
    docMask.convertTo(docMask8, CvType.CV_8U)

    val tmp = Mat()
    Core.bitwise_and(colorMask, luminanceMask, tmp)

    val restrictedMask = Mat()
    Core.bitwise_and(tmp, docMask8, restrictedMask)

    val coloredPixels = Core.countNonZero(restrictedMask)
    val totalPixels = Core.countNonZero(docMask8)

    // 8) Cleanup
    whiteBalanced.release()
    lab.release()
    channels.forEach { it.release() }
    aFloat.release()
    bFloat.release()
    aShifted.release()
    bShifted.release()
    chroma.release()
    colorMask.release()
    luminanceMask.release()
    docMask8.release()
    tmp.release()
    restrictedMask.release()
    docMask.release()

    if (totalPixels == 0) return false

    val proportion = coloredPixels.toDouble() / totalPixels.toDouble()
    return proportion > proportionThreshold
}

private fun erodeBorder(mask: Mat, quad: Quad): Mat {
    val minDim = quad.edges().minOf { it.norm() }
    var k = (minDim * 0.02).roundToInt()
    k = k.coerceIn(3, 15)
    if (k % 2 == 0) k += 1

    val kernel = Imgproc.getStructuringElement(
        Imgproc.MORPH_ELLIPSE,
        Size(k.toDouble(), k.toDouble())
    )
    val erodedMask = Mat()
    Imgproc.morphologyEx(mask, erodedMask, Imgproc.MORPH_ERODE, kernel)
    kernel.release()
    return erodedMask
}

fun quadMaskIntersection(
    originalMask: Mat,
    quad: Quad
): Mat {
    val quadMask = Mat.zeros(originalMask.size(), CvType.CV_8UC1)
    val pts = MatOfPoint(
        quad.topLeft.toCv(), quad.topRight.toCv(), quad.bottomRight.toCv(), quad.bottomLeft.toCv())
    Imgproc.fillConvexPoly(quadMask, pts, Scalar(255.0))

    val result = Mat()
    Core.bitwise_and(originalMask, quadMask, result)

    quadMask.release()
    pts.release()

    return result
}

fun applyGrayWorldToDocument(
    img: Mat,
    docMask: Mat,
): Mat {
    require(img.type() == CvType.CV_8UC3)

    val nonZero = Core.countNonZero(docMask)
    if (nonZero == 0) {
        docMask.release()
        return img.clone()
    }

    // compute mean per channel on docMask (B,G,R)
    val meanScalar = Core.mean(img, docMask) // Scalar(bMean, gMean, rMean, alpha)
    val meanB = meanScalar.`val`[0]
    val meanG = meanScalar.`val`[1]
    val meanR = meanScalar.`val`[2]

    // safety: avoid division by very small values
    val eps = 1e-6
    val meanBsafe = if (meanB < eps) eps else meanB
    val meanGsafe = if (meanG < eps) eps else meanG
    val meanRsafe = if (meanR < eps) eps else meanR

    val meanGray = (meanBsafe + meanGsafe + meanRsafe) / 3.0

    val scaleB = meanGray / meanBsafe
    val scaleG = meanGray / meanGsafe
    val scaleR = meanGray / meanRsafe

    // apply per-channel scaling only on docMask
    // convert to float
    val imgF = Mat()
    img.convertTo(imgF, CvType.CV_32FC3)

    // build scales scalar in BGR order
    val scales = Scalar(scaleB, scaleG, scaleR)

    // prepare scaled full image (float)
    val scaledF = Mat()
    Core.multiply(imgF, scales, scaledF)

    // convert scaledF back to 8U
    val scaled8 = Mat()
    scaledF.convertTo(scaled8, CvType.CV_8UC3)

    // result = original copy, then copy scaled pixels where docMask != 0
    val result = img.clone()
    scaled8.copyTo(result, docMask)

    // cleanup
    imgF.release()
    scaledF.release()
    scaled8.release()

    return result
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
