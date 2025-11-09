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
package org.fairscan.app.quad

import org.fairscan.app.Point
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.sign

// Look for 3 consecutive angles that are (almost) right angles
fun findQuadFromRightAngles(
    points: List<Point>,
    imgWidth: Int,
    imgHeight: Int,
    angleMin: Float = 60f,
    angleMax: Float = 120f
): List<Point>? {
    if (points.size < 4) return null
    val n = points.size

    val angles = mutableListOf<Double>()
    for (i in 0 until n) {
        val a = points[(i + n - 1) % n]
        val b = points[i]
        val c = points[(i + 1) % n]
        angles.add(orientedAngle(a, b, c))
    }

    var bestQuad: List<Point>? = null
    var bestScore = Double.POSITIVE_INFINITY

    for (i in 0 until n) {
        val triplet = listOf(angles[i % n], angles[(i + 1) % n], angles[(i + 2) % n])
        if (triplet.all { it in angleMin..angleMax }) {
            val a = points[(i + n - 1) % n]
            val b = points[i]
            val c = points[(i + 1) % n]
            val d = points[(i + 2) % n]
            val e = points[(i + 3) % n]

            val inter = lineIntersection2(a, b, d, e) ?: continue

            val quad = listOf(b, c, d, inter)

            // ensure inside image bounds
            if (quad.any { it.x < 0 || it.x >= imgWidth || it.y < 0 || it.y >= imgHeight }) continue

            // ensure convex
            if (!isConvex(quad)) continue

            val score = quadAngleError(quad)
            if (score < bestScore) {
                bestScore = score
                bestQuad = quad
            }
        }
    }
    return bestQuad
}

fun angleBetween(v1: Point, v2: Point): Float {
    val norm1 = sqrt(v1.x * v1.x + v1.y * v1.y) + 1e-9f
    val norm2 = sqrt(v2.x * v2.x + v2.y * v2.y) + 1e-9f
    val dot = (v1.x * v2.x + v1.y * v2.y) / (norm1 * norm2)
    val cosAngle = dot.coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(cosAngle).toDouble()).toFloat()
}

fun orientedAngle(a: Point, b: Point, c: Point): Double {
    val v1 = Point(a.x - b.x, a.y - b.y)
    val v2 = Point(c.x - b.x, c.y - b.y)
    val norm1 = sqrt(v1.x * v1.x + v1.y * v1.y) + 1e-9f
    val norm2 = sqrt(v2.x * v2.x + v2.y * v2.y) + 1e-9f
    val dot = ((v1.x * v2.x + v1.y * v2.y) / (norm1 * norm2)).coerceIn(-1.0, 1.0)
    val cross = v1.x * v2.y - v1.y * v2.x
    var angle = Math.toDegrees(acos(dot))
    if (cross < 0) angle = 360.0 - angle
    return angle
}

fun lineIntersection2(p1: Point, p2: Point, p3: Point, p4: Point): Point? {
    val denom = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
    if (abs(denom) < 1e-6f) return null
    val numX = (p1.x * p2.y - p1.y * p2.x)
    val numY = (p3.x * p4.y - p3.y * p4.x)
    val px = (numX * (p3.x - p4.x) - (p1.x - p2.x) * numY) / denom
    val py = (numX * (p3.y - p4.y) - (p1.y - p2.y) * numY) / denom
    return Point(px, py)
}

fun quadAngleError(quad: List<Point>): Double {
    var err = 0.0
    for (i in 0 until 4) {
        val a = quad[(i + 3) % 4]
        val b = quad[i]
        val c = quad[(i + 1) % 4]
        val ang = angleBetween(Point(a.x - b.x, a.y - b.y), Point(c.x - b.x, c.y - b.y))
        err += abs(ang - 90.0)
    }
    return err
}

fun isConvex(quad: List<Point>): Boolean {
    if (quad.size != 4) return false
    var sign = 0
    for (i in quad.indices) {
        val a = quad[i]
        val b = quad[(i + 1) % 4]
        val c = quad[(i + 2) % 4]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        val currentSign = cross.sign.toInt()
        if (sign == 0 && currentSign != 0) {
            sign = currentSign
        } else if (currentSign != 0 && currentSign != sign) {
            return false
        }
    }
    return true
}

