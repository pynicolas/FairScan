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
package org.fairscan.app.ui

import android.content.Context
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.state.PageThumbnail
import org.fairscan.imageprocessing.ColorMode

fun dummyNavigation(): Navigation {
    return Navigation({}, {}, {}, {}, {}, {}, {}, {})
}

fun fakeDocument(): DocumentUiModel {
    return DocumentUiModel(persistentListOf())
}

fun fakeDocument(pageIds: ImmutableList<String>, context: Context): DocumentUiModel {
    val pageKeys = pageIds.map {
        PageThumbnail(PageViewKey(it, Rotation.R0, ColorMode.COLOR), fakeImage(it, context))
    }.toImmutableList()
    return DocumentUiModel(pageKeys)
}

fun fakeImage(id: String, context: Context): Jpeg =
    Jpeg(context.assets.open("${id}.jpg").readBytes())
