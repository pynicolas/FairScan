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
import org.opencv.core.CvType.CV_8UC1
import org.opencv.core.Mat
import org.opencv.core.Mat.zeros
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.fillConvexPoly
import kotlin.math.roundToInt

fun autoColorMode(
    img: Mat,
    mask: Mask,
    quad: Quad,
    chromaThreshold: Double = 19.0,
    proportionThreshold: Double = 0.0003,
    luminanceMin: Double = 40.0,
    luminanceMax: Double = 180.0
): ColorMode {

    // Work on a reasonable size (for correct performance)
    val resizedImg = resizeForMaxPixels(img, 1024.0 * 768.0)
    val workSize = resizedImg.size()

    // 1) Compute doc mask (mask ∩ quad)
    val docMask = documentMask(mask, quad, img.size(), workSize)

    // 2) Apply white balance
    val box = Imgproc.boundingRect(docMask)
    val region = Mat(resizedImg, box)
    val regionMask = Mat(docMask, box)
    val wb = analyzeWhiteBalance(region, regionMask)
    val whiteBalanced = wb.gains.applyTo(resizedImg)
    releaseAll(region, regionMask, resizedImg)

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
    val chroma = labChroma(a, b)

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
    releaseAll(channels)
    releaseAll(whiteBalanced, lab, chroma, colorMask, luminanceMask, tmp, restrictedMask, docMask)

    if (totalPixels == 0) return ColorMode.GRAYSCALE

    val proportion = coloredPixels.toDouble() / totalPixels.toDouble()
    return if (proportion > proportionThreshold)
        ColorMode.COLOR
    else
        ColorMode.GRAYSCALE
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

    releaseAll(quadMask, pts, erodedMask, resizedMask, maskMat)

    return docMask
}
