/*
 * /*
 *  * Copyright 2025 Pierre-Yves Nicolas
 *  *
 *  * This program is free software: you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation, either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  * You should have received a copy of the GNU General Public License along with
 *  * this program. If not, see <https://www.gnu.org/licenses/>.
 *  */
 */
package org.mydomain.myscan

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap

fun detectDocumentQuad(mask: Bitmap): Quad? {
    val mat = Mat()
    Utils.bitmapToMat(mask, mat)

    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

    val blurred = Mat()
    Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

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

    val vertices = biggest?.toList()?.map { Point(it.x.toInt(), it.y.toInt()) }
    return createQuad(vertices)
}

fun extractDocument(originalBitmap: Bitmap, quad: Quad): Bitmap {
    val widthTop = norm(quad.topLeft, quad.topRight)
    val widthBottom = norm(quad.bottomLeft, quad.bottomRight)
    val maxWidth = max(widthTop, widthBottom).toInt()

    val heightLeft = norm(quad.topLeft, quad.bottomLeft)
    val heightRight = norm(quad.topRight, quad.bottomRight)
    val maxHeight = max(heightLeft, heightRight).toInt()

    val srcPoints = MatOfPoint2f(
        quad.topLeft.toCv(),
        quad.topRight.toCv(),
        quad.bottomRight.toCv(),
        quad.bottomLeft.toCv(),
    )
    val dstPoints = MatOfPoint2f(
        org.opencv.core.Point(0.0, 0.0),
        org.opencv.core.Point(maxWidth.toDouble(), 0.0),
        org.opencv.core.Point(maxWidth.toDouble(), maxHeight.toDouble()),
        org.opencv.core.Point(0.0, maxHeight.toDouble())
    )
    val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

    val inputMat = Mat()
    Utils.bitmapToMat(originalBitmap, inputMat)
    val outputMat = Mat()
    Imgproc.warpPerspective(inputMat, outputMat, transform, Size(maxWidth.toDouble(), maxHeight.toDouble()))

    val outputBitmap = createBitmap(maxWidth, maxHeight)
    Utils.matToBitmap(outputMat, outputBitmap)
    return outputBitmap
}

fun Point.toCv(): org.opencv.core.Point {
    return org.opencv.core.Point(x.toDouble(), y.toDouble())
}

private fun norm(p1: Point, p2: Point): Double {
    val dx = (p2.x - p1.x)
    val dy = (p2.y - p1.y)
    return sqrt(dx.toDouble() * dx + dy * dy)
}
