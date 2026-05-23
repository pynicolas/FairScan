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

import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sqrt

data class Vector3D(val x: Double, val y: Double, val z: Double) {
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(t: Double) = Vector3D(x * t, y * t, z * t)
    // https://en.wikipedia.org/wiki/Dot_product
    fun dotProduct(other: Vector3D) = x * other.x + y * other.y + z * other.z
    // https://en.wikipedia.org/wiki/Cross_product
    fun crossProduct(other: Vector3D) = Vector3D(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )
    fun norm() = sqrt(x * x + y * y + z * z)
}

data class CameraIntrinsics(
    // in millimeters
    val focalLength: Float,
    val sensorWidth: Float,
) {
    fun focalLengthInPixels(imageWidthInPixels: Int) =
        focalLength / sensorWidth * imageWidthInPixels
}

fun cameraIntrinsics(focalLengthInMm: Float?, sensorWidthInMm: Float?): CameraIntrinsics? {
    if (focalLengthInMm == null || sensorWidthInMm == null)
        return null
    return CameraIntrinsics(focalLengthInMm, sensorWidthInMm)
}

data class OpticalMeasures(
    val cameraIntrinsics: CameraIntrinsics,
    // in millimeters
    val subjectDistance: Float?,
)

sealed class EstimatedDimensions {
    // Dimensions in mm, when subject distance is available
    data class Physical(val widthMm: Double, val heightMm: Double) : EstimatedDimensions()
    // Dimensions in arbitrary units, only ratio is meaningful
    data class Ratio(val width: Double, val height: Double) : EstimatedDimensions()

    val aspectRatio: Double get() = when (this) {
        is Physical -> heightMm / widthMm
        is Ratio -> height / width
    }

    fun snapToStandardFormat(
        ratioTolerance: Double = 0.04,
        dimensionTolerance: Double = 0.20,
    ): EstimatedDimensions {
        if (this !is Physical) return this

        // Normalize to portrait for comparison
        val (w, h) = if (widthMm <= heightMm) widthMm to heightMm
        else heightMm to widthMm
        val portrait = widthMm <= heightMm

        for (format in PaperFormats.all) {
            val (fw, fh) = format.widthMm to format.heightMm  // format is always portrait
            val ratioError = abs((h / w) - (fh / fw)) / (fh / fw)
            val dimError = maxOf(abs(w - fw) / fw, abs(h - fh) / fh)
            if (ratioError < ratioTolerance && dimError < dimensionTolerance) {
                return if (portrait) format
                else Physical(format.heightMm, format.widthMm)
            }
        }

        return this
    }
}

object PaperFormats {
    val A3     = EstimatedDimensions.Physical(297.0, 420.0)
    val A4     = EstimatedDimensions.Physical(210.0, 297.0)
    val A5     = EstimatedDimensions.Physical(148.0, 210.0)
    val Letter = EstimatedDimensions.Physical(215.9, 279.4)
    val Legal  = EstimatedDimensions.Physical(215.9, 355.6)

    val all = listOf(A4, Letter, Legal, A5, A3)
}

/**
 * Estimates the true width and height of the document in the output image,
 * correcting for perspective distortion using projective geometry.
 *
 * Falls back to average side lengths when the geometry is degenerate
 * or the perspective is too weak to estimate reliably.
 *
 * See:
 * - https://en.wikipedia.org/wiki/Pinhole_camera_model
 * - https://www.robots.ox.ac.uk/~vgg/publications/1999/Criminisi99/criminisi99.pdf
 * - https://web.stanford.edu/class/cs231a/course_notes/02-single-view-metrology.pdf
*/
fun estimateRealDimensions(
    quad: Quad,
    imageWidth: Int,
    imageHeight: Int,
    opticalMeasures: OpticalMeasures?,
): EstimatedDimensions {

    fun averageSides(): EstimatedDimensions.Ratio {
        val w = (norm(quad.topLeft, quad.topRight) + norm(quad.bottomLeft, quad.bottomRight)) / 2
        val h = (norm(quad.topLeft, quad.bottomLeft) + norm(quad.topRight, quad.bottomRight)) / 2
        return EstimatedDimensions.Ratio(w, h)
    }

    // Homogeneous 2D point
    // https://en.wikipedia.org/wiki/Homogeneous_coordinates#Use_in_computer_graphics_and_computer_vision
    fun toH(p: Point) = Vector3D(p.x, p.y, 1.0)

    // Line through two points in homogeneous coordinates
    fun lineThrough(p1: Point, p2: Point) = toH(p1).crossProduct(toH(p2))

    // Vanishing points from pairs of opposite sides
    val v1h = lineThrough(quad.topLeft, quad.topRight)
        .crossProduct(lineThrough(quad.bottomLeft, quad.bottomRight))
    val v2h = lineThrough(quad.topLeft, quad.bottomLeft)
        .crossProduct(lineThrough(quad.topRight, quad.bottomRight))

    // Degenerate case: one pair of sides is parallel (vanishing point at infinity)
    if (v1h.z.absoluteValue < 1e-6 || v2h.z.absoluteValue < 1e-6)
        return averageSides()

    // Approximate "principal point" as image center (common assumption on mobile cameras)
    val cx = imageWidth / 2.0
    val cy = imageHeight / 2.0

    // Vanishing points in Cartesian coordinates, relative to principal point
    val v1 = Point(v1h.x / v1h.z - cx, v1h.y / v1h.z - cy)
    val v2 = Point(v2h.x / v2h.z - cx, v2h.y / v2h.z - cy)

    val f = if (opticalMeasures != null) {
        opticalMeasures.cameraIntrinsics
            .focalLengthInPixels(max(imageWidth, imageHeight)).toDouble()
    } else {
        // Focal length estimated assuming zero skew and principal point at image center.
        // Under these assumptions, the Image of the Absolute Conic (IAC) simplifies,
        // and orthogonal directions satisfy v1 · ω · v2 = 0,
        // which reduces to: f² = -(v1x·v2x + v1y·v2y)
        val f2 = -(v1.x * v2.x + v1.y * v2.y)
        if (f2 <= 0)
            return averageSides()
        sqrt(f2)
    }

    // Fall back when f is too large: document nearly fronto-parallel,
    // vanishing points are far away, making the focal length estimate unstable.
    //
    // This threshold is heuristic and tuned for typical smartphone images.
    // Note that the estimated f depends on both camera intrinsics and scene geometry,
    // so large values usually indicate low perspective rather than an actual large focal length.
    //
    // In those cases, falling back to average side lengths gives a stable approximation.
    if (f > max(imageWidth, imageHeight) * 1.2)
        return averageSides()

    // 3D directions of each pair of sides, back-projected through K⁻¹
    val d1 = Vector3D(v1.x, v1.y, f)
    val d2 = Vector3D(v2.x, v2.y, f)

    // Document plane normal: perpendicular to both edge directions
    val n = d1.crossProduct(d2)

    // Camera ray through a corner: K⁻¹ · (u, v, 1)
    fun ray(p: Point) = Vector3D((p.x - cx) / f, (p.y - cy) / f, 1.0)

    // Scale factor: either from subject distance, or arbitrary (ratio only)
    val subjectDistance = opticalMeasures?.subjectDistance?.toDouble()
    val scale: Double? = if (subjectDistance != null) {
        // Project subject distance onto the plane normal to get perpendicular distance
        val centerX = (quad.topLeft.x + quad.topRight.x + quad.bottomLeft.x + quad.bottomRight.x) / 4.0
        val centerY = (quad.topLeft.y + quad.topRight.y + quad.bottomLeft.y + quad.bottomRight.y) / 4.0
        val centerRay = ray(Point(centerX, centerY)).let { it * (1.0 / it.norm()) }
        val cosAngle = centerRay.dotProduct(n).absoluteValue
        if (cosAngle < 0.1) null  // document too tilted, unreliable
        else subjectDistance * cosAngle
    } else null

    // Intersect ray with document plane: X = t·r where t = d / (n·r)
    // When subjectDistance is unavailable, we assume an arbitrary plane distance (d = 1): absolute
    // scale is wrong, but cancels out when computing length ratios.
    fun corner3D(p: Point): Vector3D {
        val r = ray(p)
        val t = if (scale != null) scale / n.dotProduct(r) else 1.0 / n.dotProduct(r)
        return r * t
    }

    val xTL = corner3D(quad.topLeft);  val xTR = corner3D(quad.topRight)
    val xBR = corner3D(quad.bottomRight); val xBL = corner3D(quad.bottomLeft)

    // Side lengths in reconstructed 3D space
    val realW = ((xTR - xTL).norm() + (xBR - xBL).norm()) / 2
    val realH = ((xBL - xTL).norm() + (xBR - xTR).norm()) / 2

    return if (opticalMeasures != null && scale != null) {
        EstimatedDimensions.Physical(realW, realH)
    } else {
        EstimatedDimensions.Ratio(realW, realH)
    }
}
