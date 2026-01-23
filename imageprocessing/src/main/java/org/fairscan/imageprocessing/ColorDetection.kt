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
import org.opencv.core.CvType.CV_8UC1
import org.opencv.core.Mat
import org.opencv.core.Mat.zeros
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.fillConvexPoly
import kotlin.math.roundToInt

fun isColoredDocument(
    img: Mat,
    mask: Mask,
    quad: Quad,
    chromaThreshold: Double = 17.5,
    proportionThreshold: Double = 0.0003,
    luminanceMin: Double = 40.0,
    luminanceMax: Double = 180.0
): Boolean {

    // Work on a reasonable size (for correct performance)
    val resizedImg = resizeForMaxPixels(img, 1024.0 * 768.0)
    val workSize = resizedImg.size()

    // 1) Compute doc mask (mask ∩ quad)
    val docMask = documentMask(mask, quad, img.size(), workSize)

    // 2) Apply white balance only inside document
    val whiteBalanced = applyGrayWorldToDocument(resizedImg, docMask)

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
    val chroma = chroma(a, b)

    val colorMask = Mat()
    Imgproc.threshold(chroma, colorMask, chromaThreshold, 255.0, Imgproc.THRESH_BINARY)
    colorMask.convertTo(colorMask, CvType.CV_8U)

    // 6) Create luminance mask L ∈ [luminanceMin, luminanceMax]
    val luminanceMask = Mat()
    Core.inRange(luminance, Scalar(luminanceMin), Scalar(luminanceMax), luminanceMask)

    // 7) Combine colorMask & luminanceMask & docMask
    val tmp = Mat()
    Core.bitwise_and(colorMask, luminanceMask, tmp)

    val restrictedMask = Mat()
    Core.bitwise_and(tmp, docMask, restrictedMask)

    val coloredPixels = Core.countNonZero(restrictedMask)
    val totalPixels = Core.countNonZero(docMask)

    // 8) Cleanup
    resizedImg.release()
    whiteBalanced.release()
    lab.release()
    channels.forEach { it.release() }
    chroma.release()
    colorMask.release()
    luminanceMask.release()
    tmp.release()
    restrictedMask.release()
    docMask.release()

    if (totalPixels == 0) return false

    val proportion = coloredPixels.toDouble() / totalPixels.toDouble()
    return proportion > proportionThreshold
}

private fun chroma(a: Mat, b: Mat): Mat {
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

    aFloat.release()
    bFloat.release()
    aShifted.release()
    bShifted.release()

    return chroma
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

private fun documentMask(
    mask: Mask,
    quad: Quad,
    origSize: Size,
    workSize: Size,
): Mat {
    val resizedMask = Mat()
    val maskMat = mask.toMat()
    Imgproc.resize(maskMat, resizedMask, workSize, 0.0, 0.0, Imgproc.INTER_AREA)
    val resizedQuad = quad.scaledTo(
        origSize.width, origSize.height, workSize.width, workSize.height
    )
    val erodedMask = erodeBorder(resizedMask, resizedQuad)
    val quadMask = zeros(erodedMask.size(), CV_8UC1)
    val pts = MatOfPoint(
        resizedQuad.topLeft.toCv(), resizedQuad.topRight.toCv(), resizedQuad.bottomRight.toCv(), resizedQuad.bottomLeft.toCv())
    fillConvexPoly(quadMask, pts, Scalar(255.0))

    val docMask = Mat()
    Core.bitwise_and(erodedMask, quadMask, docMask)

    quadMask.release()
    pts.release()
    erodedMask.release()
    resizedMask.release()
    maskMat.release()

    return docMask
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
