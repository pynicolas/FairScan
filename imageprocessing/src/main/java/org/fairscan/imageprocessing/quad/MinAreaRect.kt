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
package org.fairscan.imageprocessing.quad

import org.fairscan.imageprocessing.Point
import kotlin.math.cos
import kotlin.math.sin

// Look for a minimal rectangle that covers a given polygon
fun minAreaRect(polygon: List<Point>, imgWidth: Int? = null, imgHeight: Int? = null): List<Point>? {
    if (polygon.size < 3) return null

    val hull = convexHull(polygon)
    if (hull.size < 3) return hull

    var bestArea = Double.POSITIVE_INFINITY
    var bestRect: List<Point>? = null

    // Test 90 rotation angles between 0 and π/2
    for (deg in 0 until 90) {
        val angle = Math.toRadians(deg.toDouble())
        val cosA = cos(angle)
        val sinA = sin(angle)

        // Rotation matrix
        val rotX = { p: Point -> p.x * cosA - p.y * sinA }
        val rotY = { p: Point -> p.x * sinA + p.y * cosA }

        val rotated = hull.map { Point(rotX(it), rotY(it)) }

        val minX = rotated.minOf { it.x }
        val maxX = rotated.maxOf { it.x }
        val minY = rotated.minOf { it.y }
        val maxY = rotated.maxOf { it.y }

        val area = (maxX - minX) * (maxY - minY)
        if (area < bestArea) {
            bestArea = area

            val rectRot = listOf(
                Point(minX, minY),
                Point(maxX, minY),
                Point(maxX, maxY),
                Point(minX, maxY)
            )

            // Apply inverse rotation
            val invX = { p: Point -> p.x * cosA + p.y * sinA }
            val invY = { p: Point -> -p.x * sinA + p.y * cosA }
            val rect = rectRot.map { Point(invX(it), invY(it)) }

            bestRect = rect
        }
    }

    if (bestRect == null) return null

    // Optionally clip within image bounds
    if (imgWidth != null && imgHeight != null) {
        val w = imgWidth - 1.0
        val h = imgHeight - 1.0
        return bestRect.map {
            Point(it.x.coerceIn(0.0, w), it.y.coerceIn(0.0, h))
        }
    }

    return bestRect
}

fun convexHull(points: List<Point>): List<Point> {
    val unique = points.distinctBy { Pair(it.x, it.y) }
    if (unique.size <= 3) return unique

    val sorted = unique.sortedWith(compareBy({ it.x }, { it.y }))

    fun cross(o: Point, a: Point, b: Point): Double {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }

    val lower = mutableListOf<Point>()
    for (p in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) {
            lower.removeAt(lower.lastIndex)
        }
        lower.add(p)
    }

    val upper = mutableListOf<Point>()
    for (p in sorted.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) {
            upper.removeAt(upper.lastIndex)
        }
        upper.add(p)
    }

    // Remove last element of each list to avoid duplication
    val hull = lower.dropLast(1) + upper.dropLast(1)
    return hull
}
