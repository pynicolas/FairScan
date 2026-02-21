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
package org.fairscan.app.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PageStoreTest {

    val page1 = PageV2("1")
    val page2 = PageV2("2")

    @Test
    fun pages() {
        val store = PageStore(listOf())
        assertThat(store.pages()).isEmpty()

        store.addOrReplace(page1)
        assertThat(store.pages()).containsExactly(page1)

        store.addOrReplace(page2)
        assertThat(store.pages()).containsExactly(page1, page2)
    }

    @Test
    fun get() {
        val store = PageStore(listOf(page1))
        assertThat(store.get(page1.id)).isEqualTo(page1)
        assertThat(store.get("x")).isNull()
    }

    @Test
    fun addOrReplace() {
        val store = PageStore(listOf())
        store.addOrReplace(page1)
        assertThat(store.pages()).containsExactly(page1)

        val page1b = PageV2("1", 90)
        store.addOrReplace(page1b)
        assertThat(store.pages()).containsExactly(page1b)
        assertThat(page1b).isNotEqualTo(page1)
    }

    @Test
    fun update() {
        val page = PageV2("3", manualRotationDegrees = 90)
        val store = PageStore(listOf(page))
        store.update(page.id) { p -> p.copy(manualRotationDegrees = p.manualRotationDegrees + 180) }
        assertThat(store.get(page.id)!!.manualRotationDegrees).isEqualTo(270)
    }

    @Test
    fun delete() {
        val store = PageStore(listOf(page1, page2))
        assertThat(store.pages()).containsExactly(page1, page2)
        store.delete(page1.id)
        assertThat(store.pages()).containsExactly(page2)
    }

    @Test
    fun clear() {
        val store = PageStore(listOf(page1, page2))
        store.clear()
        assertThat(store.pages()).isEmpty()
    }

    @Test
    fun move() {
        val store = PageStore(listOf(page1, page2))
        assertThat(store.pages()).containsExactly(page1, page2)
        store.move(page2.id, 0)
        assertThat(store.pages()).containsExactly(page2, page1)
        store.move(page2.id, 1)
        assertThat(store.pages()).containsExactly(page1, page2)
        store.move("x", 0)
        assertThat(store.pages()).containsExactly(page1, page2)
    }

}
