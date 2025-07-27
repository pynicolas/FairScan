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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class GeometryTest {

    @Test
    fun line() {
        assertThat(Line(Point(0, 0), Point(10, 0)).norm()).isEqualTo(10.0)
        assertThat(Line(Point(1, 2), Point(4, 6)).norm()).isEqualTo(5.0)
    }

    @Test
    fun createQuad() {
        val quad = createQuad(listOf(
            Point(3, 9), Point(1,2), Point(11,12), Point(10, 3)))
        assertThat(quad).isEqualTo(
            Quad(Point(1,2), Point(10, 3), Point(11,12), Point(3, 9)))
        assertThatThrownBy { createQuad(listOf()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun rotateQuad() {
        val quad = createQuad(listOf(
            Point(1,2), Point(10, 3), Point(11,12), Point(3, 9)))
        assertThat(quad.rotate90(1, 100, 50)).isEqualTo(
            createQuad(listOf(
                Point(48,1), Point(47, 10), Point(38,11), Point(41, 3)
            )))
        assertThat(quad.rotate90(2, 100, 50)).isEqualTo(
            createQuad(listOf(
                Point(99,48), Point(90, 47), Point(89,38), Point(97, 41)
            )))
        assertThat(quad.rotate90(3, 100, 50)).isEqualTo(
            createQuad(listOf(
                Point(2,99), Point(3, 90), Point(12,89), Point(9, 97)
            )))
        assertThat(quad.rotate90(4, 100, 50)).isEqualTo(quad)
        assertThat(quad.rotate90(5, 100, 50)).isEqualTo(
            quad.rotate90(1, 100, 50)
        )
    }
}
