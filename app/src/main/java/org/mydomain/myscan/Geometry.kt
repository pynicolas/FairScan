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

import kotlin.math.atan2

data class Point(val x: Int, val y: Int)

data class Line(val from: Point, val to: Point)

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
}

fun createQuad(vertices: List<Point>?): Quad? {
    if (vertices == null || vertices.size != 4) return null

    // Centroid of the points
    val cx = vertices.map { it.x }.average()
    val cy = vertices.map { it.y }.average()

    // Sort by angle from centroid (clockwise)
    val sorted = vertices.sortedWith(compareBy<Point> {
        atan2((it.y - cy).toDouble(), (it.x - cx).toDouble())
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
    return Point((x * scaleX).toInt(), (y * scaleY).toInt())
}
