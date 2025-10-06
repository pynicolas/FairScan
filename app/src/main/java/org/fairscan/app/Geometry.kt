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
package org.fairscan.app

import kotlin.math.atan2

data class Point(val x: Double, val y: Double) {
    constructor(x: Int, y: Int) : this (x.toDouble(), y.toDouble())
}

data class Line(val from: Point, val to: Point) {

    fun intersection(other: Line, eps: Double = 1e-9): Point? {
        val x1 = from.x
        val y1 = from.y
        val x2 = to.x
        val y2 = to.y
        val x3 = other.from.x
        val y3 = other.from.y
        val x4 = other.to.x
        val y4 = other.to.y

        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (kotlin.math.abs(denom) < eps) {
            return null // lines are parallel or coincident
        }

        val px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denom
        val py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denom

        return if (px.isFinite() && py.isFinite()) Point(px, py) else null
    }

    fun norm(): Double {
        return norm(from, to)
    }
}

fun norm(p1: Point, p2: Point): Double {
    val dx = (p2.x - p1.x)
    val dy = (p2.y - p1.y)
    return kotlin.math.hypot(dx, dy)
}

data class Quad(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point
) {
    fun edges(): List<Line> {
        return listOf(
            Line(topLeft, topRight),
            Line(topRight, bottomRight),
            Line(bottomRight, bottomLeft),
            Line(bottomLeft, topLeft))
    }

    fun rotate90(iterations: Int, imageWidth: Int, imageHeight: Int): Quad {
        val rotatedPoints = listOf(
            rotate90(topLeft, imageWidth, imageHeight, iterations),
            rotate90(topRight, imageWidth, imageHeight, iterations),
            rotate90(bottomRight, imageWidth, imageHeight, iterations),
            rotate90(bottomLeft, imageWidth, imageHeight, iterations)
        )
        return createQuad(rotatedPoints)
    }
    private fun rotate90(p: Point, width: Int, height: Int, iterations: Int): Point {
        return when (iterations % 4) {
            1 -> Point(height - p.y, p.x)         // 90°
            2 -> Point(width - p.x, height - p.y) // 180°
            3 -> Point(p.y, width - p.x)          // 270°
            else -> p                                      // 0°
        }
    }
}

fun createQuad(vertices: List<Point>): Quad {
    require(vertices.size == 4)

    // Centroid of the points
    val cx = vertices.map { it.x }.average()
    val cy = vertices.map { it.y }.average()

    // Sort by angle from centroid (clockwise)
    val sorted = vertices.sortedWith(compareBy {
        atan2(it.y - cy, it.x - cx)
    })

    return Quad(sorted[0], sorted[1], sorted[2], sorted[3])
}

fun Quad.scaledTo(fromWidth: Int, fromHeight: Int, toWidth: Int, toHeight: Int): Quad {
    val scaleX = toWidth.toFloat() / fromWidth
    val scaleY = toHeight.toFloat() / fromHeight
    return Quad(
        topLeft = topLeft.scaled(scaleX, scaleY),
        topRight = topRight.scaled(scaleX, scaleY),
        bottomRight = bottomRight.scaled(scaleX, scaleY),
        bottomLeft = bottomLeft.scaled(scaleX, scaleY)
    )
}

fun Point.scaled(scaleX: Float, scaleY: Float): Point {
    return Point((x * scaleX), (y * scaleY))
}

fun polygonArea(pts: List<Point>): Double {
    var area = 0.0
    for (i in pts.indices) {
        val j = (i + 1) % pts.size
        area += pts[i].x * pts[j].y - pts[j].x * pts[i].y
    }
    return kotlin.math.abs(area) / 2.0
}

fun simplifyPolygonToQuad(ptsInput: List<Point>): List<Point> {
    var pts = ptsInput.toList()
    while (pts.size > 4) {
        var bestArea = Double.MAX_VALUE
        var bestPts: List<Point>? = null
        val n = pts.size
        for (i in 0 until n) {
            val prev = pts[(i - 1 + n) % n]
            val curr = pts[i]
            val next = pts[(i + 1) % n]
            val next2 = pts[(i + 2) % n]

            val l1 = Line(prev, curr)
            val l2 = Line(next, next2)
            val inter = l1.intersection(l2) ?: continue

            // Remove curr and next, insert intersection
            val newPts = mutableListOf<Point>()
            for (j in 0 until n) {
                if (j == i || j == (i + 1) % n) continue
                newPts.add(pts[j])
                if (j == (i - 1 + n) % n) newPts.add(inter)
            }

            val area = polygonArea(newPts)
            if (area < bestArea) {
                bestArea = area
                bestPts = newPts
            }
        }

        if (bestPts == null) break
        pts = bestPts
    }
    return pts
}