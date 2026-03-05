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

import org.opencv.core.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
Instead of detecting corners (like Douglas-Peucker), this algorithm detects
the four dominant sides of the document by segmenting the contour according
to stable edge orientations, then fits lines and intersects them to
reconstruct the quadrilateral.
*/
fun findQuadFromContourOrientation(
    contour: List<Point>,
    smoothWindow: Int = 5,
    maxAngleVar: Double = Math.toRadians(5.0),
    mergeAngle: Double = Math.toRadians(7.0),
    minSideLengthRatio: Double = 0.02
): List<Point>? {

    if (contour.size < 20) return null

    val angles = computeSmoothedAngles(contour, smoothWindow)

    val perimeter = contour.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

    val minLength = perimeter * minSideLengthRatio
    val segments = extractSegments(contour, angles, maxAngleVar, minLength)
    val mergedSegments = mergeSegments(segments, mergeAngle)
    val dominantSegments = selectDominantSegments(
        mergedSegments,
        maxCount = 4,
        minAngleSeparation = Math.toRadians(25.0)
    )

    if (dominantSegments.size != 4) return null

    val lines = dominantSegments.map {
        val points = if (it.start < it.end)
            contour.subList(it.start, it.end)
        else
            contour.subList(it.start, contour.size) + contour.subList(0, it.end)
        fitLine(points)
    }

    val corners = mutableListOf<Point>()
    for (i in 0 until 4) {
        val p = intersectLines(lines[i], lines[(i + 1) % 4])
            ?: return null
        corners += p
    }
    return corners
}

private fun normalizeAngle(a: Double): Double {
    var x = a
    while (x <= -Math.PI) x += 2 * Math.PI
    while (x > Math.PI) x -= 2 * Math.PI
    return x
}

private fun angleDiff(a: Double, b: Double): Double =
    abs(normalizeAngle(a - b))

private data class Line(
    val p: Point,
    val d: Point
)

private fun fitLine(points: List<Point>): Line {
    val cx = points.map { it.x }.average()
    val cy = points.map { it.y }.average()

    var xx = 0.0
    var xy = 0.0
    var yy = 0.0

    for (p in points) {
        val dx = p.x - cx
        val dy = p.y - cy
        xx += dx * dx
        xy += dx * dy
        yy += dy * dy
    }

    val theta = 0.5 * atan2(2 * xy, xx - yy)
    val dir = Point(cos(theta), sin(theta))

    return Line(Point(cx, cy), dir)
}

private fun intersectLines(l1: Line, l2: Line): Point? {
    val x1 = l1.p.x
    val y1 = l1.p.y
    val x2 = x1 + l1.d.x
    val y2 = y1 + l1.d.y

    val x3 = l2.p.x
    val y3 = l2.p.y
    val x4 = x3 + l2.d.x
    val y4 = y3 + l2.d.y

    val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if (abs(denom) < 1e-6) return null

    val px =
        ((x1*y2 - y1*x2)*(x3 - x4) - (x1 - x2)*(x3*y4 - y3*x4)) / denom
    val py =
        ((x1*y2 - y1*x2)*(y3 - y4) - (y1 - y2)*(x3*y4 - y3*x4)) / denom

    return Point(px, py)
}

private data class ContourSegment(
    val start: Int,
    val end: Int,
    val angle: Double,
    val length: Double
)

private fun extractSegments(
    contour: List<Point>,
    angles: DoubleArray,
    maxAngleVar: Double,
    minLength: Double
): List<ContourSegment> {

    val n = contour.size
    val result = mutableListOf<ContourSegment>()

    val startIndex = findBestStartIndex(angles)

    var start = startIndex
    var refAngle = angles[startIndex]

    fun segmentLength(s: Int, e: Int): Double {
        var len = 0.0
        var i = s
        while (i != e) {
            val j = (i + 1) % n
            len += hypot(
                contour[j].x - contour[i].x,
                contour[j].y - contour[i].y
            )
            i = j
        }
        return len
    }

    var steps = 1
    while (steps <= n) {
        val idx = (startIndex + steps) % n

        if (steps < n && angleDiff(angles[idx], refAngle) < maxAngleVar) {
            refAngle = angleMean(refAngle, angles[idx])
        } else {
            val len = segmentLength(start, idx)
            if (len >= minLength) {
                result += ContourSegment(start, idx, refAngle, len)
            }
            start = idx
            refAngle = angles[idx]
        }

        steps++
    }

    return result
}

private fun findBestStartIndex(angles: DoubleArray): Int {
    val n = angles.size
    var bestIndex = 0
    var bestDelta = 0.0

    for (i in 0 until n) {
        val j = (i + 1) % n
        val d = angleDiff(angles[i], angles[j])
        if (d > bestDelta) {
            bestDelta = d
            bestIndex = j
        }
    }
    return bestIndex
}

private fun angleMean(a: Double, b: Double): Double {
    val x = cos(a) + cos(b)
    val y = sin(a) + sin(b)
    return atan2(y, x)
}

private fun computeSmoothedAngles(
    contour: List<Point>,
    window: Int
): DoubleArray {
    val n = contour.size

    // --- Step 1: raw angles ---
    val angles = DoubleArray(n)
    for (i in 0 until n) {
        val p0 = contour[(i - 1 + n) % n]
        val p1 = contour[(i + 1) % n]
        angles[i] = atan2(p1.y - p0.y, p1.x - p0.x)
    }

    // --- Step 2: precompute cos/sin ---
    val cosA = DoubleArray(n)
    val sinA = DoubleArray(n)
    for (i in 0 until n) {
        cosA[i] = cos(angles[i])
        sinA[i] = sin(angles[i])
    }

    // --- Step 3: sliding window smoothing ---
    val smooth = DoubleArray(n)

    var sx = 0.0
    var sy = 0.0

    // initial window centered on index 0
    for (k in -window..window) {
        val idx = (k + n) % n
        sx += cosA[idx]
        sy += sinA[idx]
    }

    smooth[0] = atan2(sy, sx)

    for (i in 1 until n) {
        val outIdx = (i - window - 1 + n) % n
        val inIdx  = (i + window) % n
        sx -= cosA[outIdx]
        sy -= sinA[outIdx]
        sx += cosA[inIdx]
        sy += sinA[inIdx]
        smooth[i] = atan2(sy, sx)
    }
    return smooth
}

private fun mergeSegments(
    segments: List<ContourSegment>,
    angleThreshold: Double
): List<ContourSegment> {
    if (segments.isEmpty()) return emptyList()
    if (segments.size <= 4) return segments

    val merged = mutableListOf<ContourSegment>()
    var cur = segments[0]

    for (i in 1 until segments.size) {
        val p = segments[i]
        if (angleDiff(p.angle, cur.angle) < angleThreshold) {
            cur = ContourSegment(
                cur.start,
                p.end,
                angleMean(cur.angle, p.angle),
                cur.length + p.length
            )
        } else {
            merged += cur
            cur = p
        }
    }
    merged += cur
    return merged
}

private fun selectDominantSegments(
    segments: List<ContourSegment>,
    maxCount: Int,
    minAngleSeparation: Double
): List<ContourSegment> {

    val sorted = segments.sortedByDescending { it.length }
    val selected = mutableListOf<ContourSegment>()

    for (p in sorted) {
        val tooClose = selected.any { s ->
            angleDiff(p.angle, s.angle) < minAngleSeparation
        }

        if (!tooClose) {
            selected += p
            if (selected.size == maxCount) break
        }
    }

    return selected.sortedBy { it.start }
}
