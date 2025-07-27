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
package org.mydomain.myscan

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

fun detectDocumentQuad(mask: Bitmap, minQuadAreaRatio: Double = 0.02): Quad? {
    val mat = Mat()
    Utils.bitmapToMat(mask, mat)

    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

    val refinedMask = refineMask(gray)

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

        if (approx.total() == 4L) {
            val area = abs(Imgproc.contourArea(approx))
            if (area > maxArea) {
                maxArea = area
                biggest = approx
            }
        }
    }

    if (maxArea < mask.width * mask.height * minQuadAreaRatio) {
        return null
    }

    val vertices = biggest?.toList()?.map { Point(it.x.toInt(), it.y.toInt()) }
    return if (vertices?.size == 4) createQuad(vertices) else null
}

/**
 * Applies morphological operations to improve a document mask.
 */
fun refineMask(original: Mat): Mat {
    // Step 0: Ensure the mask is binary (just in case)
    val binaryMask = Mat()
    Imgproc.threshold(original, binaryMask, 0.0, 255.0, Imgproc.THRESH_BINARY)

    // Step 1: Closing (fills small holes)
    val kernelClose = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
    val closed = Mat()
    Imgproc.morphologyEx(binaryMask, closed, Imgproc.MORPH_CLOSE, kernelClose)

    // Step 2: Gentle opening (removes isolated noise)
    val kernelOpen = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
    val opened = Mat()
    Imgproc.morphologyEx(closed, opened, Imgproc.MORPH_OPEN, kernelOpen)

    // Step 3: Light dilation (connects almost touching parts)
    val kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
    val dilated = Mat()
    Imgproc.dilate(opened, dilated, kernelDilate, org.opencv.core.Point(-1.0, -1.0), 1)

    return dilated
}

fun extractDocument(originalBitmap: Bitmap, quad: Quad, rotationDegrees: Int): Bitmap {
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
        org.opencv.core.Point(targetWidth.toDouble(), 0.0),
        org.opencv.core.Point(targetWidth.toDouble(), targetHeight.toDouble()),
        org.opencv.core.Point(0.0, targetHeight.toDouble())
    )
    val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

    val inputMat = Mat()
    Utils.bitmapToMat(originalBitmap, inputMat)
    val outputMat = Mat()
    val outputSize = Size(targetWidth.toDouble(), targetHeight.toDouble())
    Imgproc.warpPerspective(inputMat, outputMat, transform, outputSize)

    val resized = resize(outputMat, 1500.0)
    val enhanced = enhanceCapturedImage(resized)
    val rotated = rotate(enhanced, rotationDegrees)

    return toBitmap(rotated)
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

private fun toBitmap(mat: Mat): Bitmap {
    val outputBitmap = createBitmap(mat.cols(), mat.rows())
    Utils.matToBitmap(mat, outputBitmap)
    return outputBitmap
}

fun Point.toCv(): org.opencv.core.Point {
    return org.opencv.core.Point(x.toDouble(), y.toDouble())
}

