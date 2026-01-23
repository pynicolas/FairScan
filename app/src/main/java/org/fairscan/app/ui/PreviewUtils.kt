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
import android.graphics.BitmapFactory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.state.DocumentUiModel

fun dummyNavigation(): Navigation {
    return Navigation({}, {}, {}, {}, {}, {}, {}, {})
}

fun fakeDocument(): DocumentUiModel {
    return DocumentUiModel(persistentListOf(), { _ -> null }, { _ -> null })
}

fun fakeDocument(pageIds: ImmutableList<String>, context: Context): DocumentUiModel {
    val loader = { key: PageViewKey ->
        context.assets.open("${key.pageId}.jpg").use { input ->
            BitmapFactory.decodeStream(input)
        }
    }
    val pageKeys = pageIds.map { PageViewKey(it, Rotation.R0) }.toImmutableList()
    return DocumentUiModel(pageKeys, loader, loader)
}
