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
package org.fairscan.app.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.fairscan.app.domain.Rotation.Companion.fromDegrees
import org.fairscan.app.domain.Rotation.R0
import org.fairscan.app.domain.Rotation.R180
import org.fairscan.app.domain.Rotation.R270
import org.fairscan.app.domain.Rotation.R90
import org.junit.Test

class RotationTest {

    @Test
    fun fromDegreesFunction() {
        assertThat(fromDegrees(0)).isEqualTo(R0)
        assertThat(fromDegrees(90)).isEqualTo(R90)
        assertThat(fromDegrees(180)).isEqualTo(R180)
        assertThat(fromDegrees(270)).isEqualTo(R270)
        assertThat(fromDegrees(360)).isEqualTo(R0)
        assertThat(fromDegrees(-90)).isEqualTo(R270)
        assertThatThrownBy { fromDegrees(30) }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun add() {
        assertThat(R0.add(R90)).isEqualTo(R90)
        assertThat(R90.add(R90)).isEqualTo(R180)
        assertThat(R90.add(R180)).isEqualTo(R270)
        assertThat(R180.add(R180)).isEqualTo(R0)
        assertThat(R180.add(R270)).isEqualTo(R90)
    }
}
