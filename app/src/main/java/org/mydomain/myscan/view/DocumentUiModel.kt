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
package org.mydomain.myscan.view

import android.graphics.Bitmap

data class DocumentUiModel(
    private val pageIds: List<String>,
    private val imageLoader: (String) -> Bitmap?
) {
    fun pageCount(): Int {
        return pageIds.size
    }
    fun pageId(index: Int): String {
        return pageIds[index]
    }
    fun isEmpty(): Boolean {
        return pageIds.isEmpty()
    }
    fun lastIndex(): Int {
        return pageIds.lastIndex
    }
    fun load(index: Int): Bitmap? {
        return imageLoader(pageIds[index])
    }
}