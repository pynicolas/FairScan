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

class PageStore(pages: List<PageV2>) {

    private val pages = LinkedHashMap<String, PageV2>()
        .also { map -> pages.forEach { map.put(it.id, it) } }

    fun pages(): List<PageV2> =
        pages.values.toList()

    fun get(id: String): PageV2? =
        pages[id]

    fun addOrReplace(page: PageV2) =
        pages.put(page.id, page)

    fun update(id: String, transform: (PageV2) -> PageV2) {
        val page = pages[id] ?: return
        pages[id] = transform(page)
    }

    fun delete(id: String) = pages.remove(id)

    fun clear() = pages.clear()

    fun move(id: String, newIndex: Int) {
        val page = pages[id] ?: return

        val entries = pages.entries.toList()
            .filterNot { it.key == id }

        pages.clear()

        val safeIndex = newIndex.coerceIn(0, entries.size)
        entries.take(safeIndex).forEach { pages[it.key] = it.value }
        pages.put(id, page)
        entries.drop(safeIndex).forEach { pages[it.key] = it.value }
    }
}
