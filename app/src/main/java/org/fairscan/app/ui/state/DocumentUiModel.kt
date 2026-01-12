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
package org.fairscan.app.ui.state

import android.graphics.Bitmap
import kotlinx.collections.immutable.ImmutableList
import org.fairscan.app.domain.PageViewKey

data class DocumentUiModel(
    val pageKeys: ImmutableList<PageViewKey>,
    private val imageLoader: (PageViewKey) -> Bitmap?,
    private val thumbnailLoader: (PageViewKey) -> Bitmap?
) {
    fun pageCount(): Int {
        return pageKeys.size
    }
    fun pageId(index: Int): String {
        return pageKeys[index].pageId
    }
    fun isEmpty(): Boolean {
        return pageKeys.isEmpty()
    }
    fun lastIndex(): Int {
        return pageKeys.lastIndex
    }
    fun load(index: Int): Bitmap? {
        return imageLoader(pageKeys[index])
    }
    fun loadThumbnail(index: Int): Bitmap? {
        return thumbnailLoader(pageKeys[index])
    }
}