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

import org.fairscan.imageprocessing.quad.findQuadFromContourOrientation
import org.fairscan.imageprocessing.quad.minAreaRect
import org.fairscan.imageprocessing.quad.scoreQuadAgainstProbmap
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

interface Mask {
    val width: Int
    val height: Int
    fun toMat(): Mat
}

fun detectDocumentQuad(mask: Mask, originalSize: ImageSize, isLiveAnalysis: Boolean): Quad? {
    val mat = mask.toMat()
    // Best thresholds on test dataset: {0.95=146, 0.85=39, 0.75=35, 0.90=8, 0.70=1, 0.35=1}
    val thresholds =
        if (isLiveAnalysis) listOf(0.9) else listOf(0.5, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95)
    var vertices = findQuadFromOrientationWithAdaptiveThreshold(mat, originalSize, thresholds)
        ?.map { Point(it.x, it.y) }
    if (vertices == null && !isLiveAnalysis) {
        // Fallback: bounding rectangle
        val biggest = biggestContour(mat)
        if (biggest != null) {
            val polygon = biggest.toList().map { Point(it.x, it.y) }
            vertices = minAreaRect(polygon, mask.width, mask.height)
        }
    }
    return if (vertices?.size == 4) createQuad(vertices) else null
}

fun findQuadFromOrientationWithAdaptiveThreshold(
    maskMat: Mat, originalSize: ImageSize, thresholds: List<Double>
): List<org.opencv.core.Point>? {
    val probmapU8 = Mat()
    val probmap = maskMat
    probmap.convertTo(probmapU8, CvType.CV_8U, 255.0)
    val probmapSmooth = Mat()
    Imgproc.GaussianBlur(probmapU8, probmapSmooth, Size(3.0, 3.0), 0.0)

    var bestQuad: List<org.opencv.core.Point>? = null
    var bestScore = 0.0
    for (thr in thresholds) {
        val bin = Mat()
        Imgproc.threshold(probmapSmooth, bin, thr * 255.0, 255.0, Imgproc.THRESH_BINARY)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_CLOSE, kernel)
        val quad = findQuadFromOrientation(bin, originalSize)
        if (quad != null && isValidQuad(quad, originalSize)) {
            val probFloat = Mat()
            probmap.convertTo(probFloat, CvType.CV_32F)
            val score = scoreQuadAgainstProbmap(quad, probFloat, minQuadAreaRatio = 0.02)
            if (score > bestScore) {
                bestScore = score
                bestQuad = quad
            }
        }
        bin.release()
    }

    probmapSmooth.release()
    probmapU8.release()
    return bestQuad
}

fun isValidQuad(quad: List<org.opencv.core.Point>, originalSize: ImageSize): Boolean {
    return quad.all {
          it.x >= 0 && it.x <= originalSize.width
       && it.y >= 0 && it.y <= originalSize.height
    }
}

fun findQuadFromOrientation(maskMat: Mat, originalSize: ImageSize): List<org.opencv.core.Point>? {
    val contour = biggestContour(maskMat)
    contour?:return null

    val scaleX = originalSize.width / maskMat.size().width
    val scaleY = originalSize.height / maskMat.size().height

    return findQuadFromContourOrientation(
        contour.toList().map { org.opencv.core.Point(it.x * scaleX, it.y * scaleY) }
    )?.map { org.opencv.core.Point(it.x / scaleX, it.y / scaleY) }
}

fun biggestContour(mat: Mat): MatOfPoint? {
    val refinedMask = refineMask(mat)

    val blurred = Mat()
    Imgproc.GaussianBlur(refinedMask, blurred, Size(5.0, 5.0), 0.0)

    val edges = Mat()
    Imgproc.Canny(blurred, edges, 75.0, 200.0)

    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE)

    var biggest: MatOfPoint? = null
    var maxArea = 0.0

    for (contour in contours) {
        val area = abs(Imgproc.contourArea(contour))
        if (area > maxArea) {
            maxArea = area
            biggest = contour
        }
    }
    return biggest
}

/**
 * Applies morphological operations to improve a document mask.
 */
fun refineMask(original: Mat): Mat {
    // Step 0: Ensure the mask is binary (just in case)
    val binaryMask = Mat()
    Imgproc.threshold(original, binaryMask, 128.0, 255.0, Imgproc.THRESH_BINARY)

    // Step 1: Closing (fills small holes)
    val kernelClose = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
    val closed = Mat()
    Imgproc.morphologyEx(binaryMask, closed, Imgproc.MORPH_CLOSE, kernelClose)

    // Step 2: Gentle opening (removes isolated noise)
    val kernelOpen = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
    val opened = Mat()
    Imgproc.morphologyEx(closed, opened, Imgproc.MORPH_OPEN, kernelOpen)

    return opened
}

fun extractDocument(
    inputMat: Mat,
    quad: Quad,
    rotationDegrees: Int,
    isColored: Boolean,
    maxPixels: Long,
): Mat {
    val widthTop = norm(quad.topLeft, quad.topRight)
    val widthBottom = norm(quad.bottomLeft, quad.bottomRight)
    val targetWidth = (widthTop + widthBottom) / 2

    val heightLeft = norm(quad.topLeft, quad.bottomLeft)
    val heightRight = norm(quad.topRight, quad.bottomRight)
    val targetHeight = (heightLeft + heightRight) / 2

    val srcPoints = MatOfPoint2f(
        quad.topLeft.toCv(),
        quad.topRight.toCv(),
        quad.bottomRight.toCv(),
        quad.bottomLeft.toCv(),
    )
    val dstPoints = MatOfPoint2f(
        org.opencv.core.Point(0.0, 0.0),
        org.opencv.core.Point(targetWidth, 0.0),
        org.opencv.core.Point(targetWidth, targetHeight),
        org.opencv.core.Point(0.0, targetHeight)
    )
    val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

    val warped = Mat()
    val outputSize = Size(targetWidth, targetHeight)
    Imgproc.warpPerspective(inputMat, warped, transform, outputSize)

    val resized = resizeForMaxPixels(warped, maxPixels.toDouble())
    val enhanced = enhanceCapturedImage(resized, isColored)
    val rotated = rotate(enhanced, rotationDegrees)

    warped.release()
    resized.release()
    enhanced.release()

    return rotated
}

fun rotate(input: Mat, degrees: Int): Mat {
    val output = Mat()
    when ((degrees % 360 + 360) % 360) {
        0 -> input.copyTo(output)
        90 -> Core.rotate(input, output, Core.ROTATE_90_CLOCKWISE)
        180 -> Core.rotate(input, output, Core.ROTATE_180)
        270 -> Core.rotate(input, output, Core.ROTATE_90_COUNTERCLOCKWISE)
        else -> throw IllegalArgumentException("Only 0, 90, 180, 270 degrees are supported")
    }
    return output
}

fun Point.toCv(): org.opencv.core.Point {
    return org.opencv.core.Point(x, y)
}

fun Size.toImageSize(): ImageSize {
    return ImageSize(width, height)
}
