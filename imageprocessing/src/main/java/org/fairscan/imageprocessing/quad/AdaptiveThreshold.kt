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
package org.fairscan.imageprocessing.quad

import org.opencv.core.Mat
import org.opencv.core.CvType
import org.opencv.core.Size
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

// Look for a threshold for which we find a quad in the mask
fun detectDocumentQuadFromProbmap(
    probmap: Mat,
    thresholds: List<Double>,
    useOtsu: Boolean = true,
    minQuadAreaRatio: Double = 0.02
): List<Point>? {
    val probmapU8 = Mat()
    probmap.convertTo(probmapU8, CvType.CV_8U, 255.0)
    val probmapSmooth = Mat()
    Imgproc.GaussianBlur(probmapU8, probmapSmooth, Size(3.0, 3.0), 0.0)

    var bestScore = 0.0
    var bestQuad: List<Point>? = null

    // 1) Otsu
    if (useOtsu) {
        val otsu = Mat()
        Imgproc.threshold(probmapSmooth, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        val quad = findQuadFromBinaryMask(otsu, minQuadAreaRatio)
        if (quad != null) {
            val probFloat = Mat()
            probmap.convertTo(probFloat, CvType.CV_32F)
            val sc = scoreQuadAgainstProbmap(quad, probFloat)
            if (sc > bestScore) {
                bestScore = sc
                bestQuad = quad
            }
        }
    }

    // 2) Threshold sweep
    for (thr in thresholds) {
        val bin = Mat()
        Imgproc.threshold(probmapSmooth, bin, thr * 255.0, 255.0, Imgproc.THRESH_BINARY)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_CLOSE, kernel)
        val quad = findQuadFromBinaryMask(bin, minQuadAreaRatio)
        if (quad != null) {
            val probFloat = Mat()
            probmap.convertTo(probFloat, CvType.CV_32F)
            val sc = scoreQuadAgainstProbmap(quad, probFloat)
            if (sc > bestScore) {
                bestScore = sc
                bestQuad = quad
            }
        }
    }

    return bestQuad
}

// Fill polygon and return binary mask (0/1)
fun makePolygonMask(size: Size, polygon: List<Point>): Mat {
    val mask = Mat.zeros(size, CvType.CV_8U)
    val pts = MatOfPoint(*polygon.toTypedArray())
    Imgproc.fillPoly(mask, listOf(pts), Scalar(1.0))
    return mask
}

// Compute score between quad and probmap
fun scoreQuadAgainstProbmap(quad: List<Point>, probmap: Mat): Double {
    val mask = makePolygonMask(probmap.size(), quad)
    val maskFloat = Mat()
    mask.convertTo(maskFloat, CvType.CV_32F)
    val masked = Mat()
    Core.multiply(probmap, maskFloat, masked)
    val meanProb = Core.sumElems(masked).`val`[0] / Core.sumElems(maskFloat).`val`[0]
    val areaRatio = Core.sumElems(maskFloat).`val`[0] / (probmap.rows() * probmap.cols())
    return meanProb * (0.7 + 0.3 * areaRatio)
}

// Find largest quadrilateral in a binary mask
fun findQuadFromBinaryMask(binMask: Mat, minQuadAreaRatio: Double = 0.02): List<Point>? {
    val blurred = Mat()
    Imgproc.GaussianBlur(binMask, blurred, Size(5.0, 5.0), 0.0)
    val edges = Mat()
    Imgproc.Canny(blurred, edges, 75.0, 200.0)

    val contours = mutableListOf<MatOfPoint>()
    Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

    var biggest: MatOfPoint2f? = null
    var maxArea = 0.0
    for (cnt in contours) {
        val cnt2f = MatOfPoint2f(*cnt.toArray())
        val peri = Imgproc.arcLength(cnt2f, true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(cnt2f, approx, 0.02 * peri, true)
        if (approx.rows() == 4) {
            val area = abs(Imgproc.contourArea(approx))
            if (area > maxArea) {
                maxArea = area
                biggest = approx
            }
        }
    }
    val totalArea = binMask.rows() * binMask.cols().toDouble()
    return if (maxArea > totalArea * minQuadAreaRatio && biggest != null) {
        biggest.toList()
    } else null
}
