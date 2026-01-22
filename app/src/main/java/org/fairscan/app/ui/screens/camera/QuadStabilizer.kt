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
package org.fairscan.app.ui.screens.camera

import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.norm

class QuadStabilizer {

    private var stableCount = 0
    private var lastRawQuad: Quad? = null

    fun update(rawQuad: Quad?): Quad? {
        lastRawQuad = rawQuad

        if (rawQuad == null) {
            stableCount = 0
            return null
        }

        val lastRaw = lastRawQuad
        if (lastRaw == null) {
            stableCount = 1
            return null
        }

        val dist = lastRaw.maxCornerDistanceTo(rawQuad)
        // 20f is based on the assumption that the preview has a size of 640×480
        if (dist < 20f) {
            stableCount++
        } else {
            stableCount = 1
        }

        return if (stableCount >= 3) rawQuad else null
    }
}

private fun Quad.maxCornerDistanceTo(other: Quad): Float {
    return listOf(
        norm(topLeft, other.topLeft),
        norm(topRight, other.topRight),
        norm(bottomRight, other.bottomRight),
        norm(bottomLeft, other.bottomLeft),
    ).max().toFloat()
}

fun lerp(a: Point, b: Point, alpha: Float): Point {
    return Point(
        x = a.x + alpha * (b.x - a.x),
        y = a.y + alpha * (b.y - a.y)
    )
}

fun lerpQuad(a: Quad, b: Quad, alpha: Float): Quad {
    return Quad(
        topLeft = lerp(a.topLeft, b.topLeft, alpha),
        topRight = lerp(a.topRight, b.topRight, alpha),
        bottomRight = lerp(a.bottomRight, b.bottomRight, alpha),
        bottomLeft = lerp(a.bottomLeft, b.bottomLeft, alpha),
    )
}
