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

import org.fairscan.imageprocessing.quad.detectDocumentQuadFromProbmap
import org.fairscan.imageprocessing.quad.findQuadFromRightAngles
import org.fairscan.imageprocessing.quad.minAreaRect
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

interface Mask {
    val width: Int
    val height: Int
    fun toMat(): Mat
}

data class PageAnalysis(
    val isColored: Boolean,
)

data class ExtractedDocument(
    val image: Mat,
    val pageAnalysis: PageAnalysis,
)

fun detectDocumentQuad(mask: Mask, isLiveAnalysis: Boolean, minQuadAreaRatio: Double = 0.02): Quad? {
    val mat = mask.toMat()
    val (biggest: MatOfPoint2f?, area) = biggestContour(mat)
    var vertices: List<Point>?
    if (biggest != null && biggest.total() == 4L && area > mask.width * mask.height * minQuadAreaRatio) {
        vertices = biggest.toList()?.map { Point(it.x, it.y) }
    } else {

        // Fallback 1: adjust threshold
        val thresholds =
            if (isLiveAnalysis) listOf(25.0, 50.0, 75.0) else (0..12).map { 0.2 + it * 0.05 }
        vertices = detectDocumentQuadFromProbmap(mat, thresholds)
            ?.map { Point(it.x, it.y) }
        if (vertices == null && biggest != null && biggest.total() > 4) {

            // Fallback 2: look for right angles
            val polygon = biggest.toList().map { Point(it.x, it.y) }
            vertices = findQuadFromRightAngles(polygon, mask.width, mask.height)
            if (vertices == null && !isLiveAnalysis) {

                // Fallback 3: bounding rectangle
                vertices = minAreaRect(polygon, mask.width, mask.height)
            }
        }
    }
    return if (vertices?.size == 4) createQuad(vertices) else null
}

private fun biggestContour(mat: Mat): Pair<MatOfPoint2f?, Double> {
    val refinedMask = refineMask(mat)

    val blurred = Mat()
    Imgproc.GaussianBlur(refinedMask, blurred, Size(5.0, 5.0), 0.0)

    val edges = Mat()
    Imgproc.Canny(blurred, edges, 75.0, 200.0)

    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

    var biggest: MatOfPoint2f? = null
    var maxArea = 0.0

    for (contour in contours) {
        val contour2f = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(contour2f, true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

        val area = abs(Imgproc.contourArea(approx))
        if (area > maxArea) {
            maxArea = area
            biggest = approx
        }
    }
    return Pair(biggest, maxArea)
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
    mask: Mask,
): ExtractedDocument {
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

    val outputMat = Mat()
    val outputSize = Size(targetWidth, targetHeight)
    Imgproc.warpPerspective(inputMat, outputMat, transform, outputSize)

    val resized = resize(outputMat, 1500.0)
    val isColored = isColoredDocument(inputMat, mask, quad)
    val enhanced = enhanceCapturedImage(resized, isColored)
    val rotated = rotate(enhanced, rotationDegrees)

    return ExtractedDocument(rotated, PageAnalysis(isColored))
}

fun resize(original: Mat, targetMax: Double): Mat {
    val origSize = original.size()
    if (max(origSize.width, origSize.height) < targetMax)
        return original;
    var targetWidth = targetMax
    var targetHeight = origSize.height * targetWidth / origSize.width
    if (origSize.width < origSize.height) {
        targetHeight = targetMax
        targetWidth = origSize.width * targetHeight / origSize.height
    }
    val result = Mat()
    Imgproc.resize(original, result, Size(targetWidth, targetHeight), 0.0, 0.0, Imgproc.INTER_AREA)
    return result
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

