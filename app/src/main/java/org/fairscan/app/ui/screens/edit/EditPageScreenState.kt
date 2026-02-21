/*
 * Copyright 2026 Philipp Hasper
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
package org.fairscan.app.ui.screens.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Quad

class EditPageScreenState {
    var bitmap by mutableStateOf<android.graphics.Bitmap?>(null)
    var containerSize by mutableStateOf<IntSize?>(null)
    var editableQuad by mutableStateOf<Quad?>(null)
    var draggedCornerIndex by mutableIntStateOf(-1)
    var draggedEdgeIndex by mutableIntStateOf(-1)

    fun updateQuad(newQuad: Quad) {
        editableQuad = newQuad
    }

    fun startCornerDrag(cornerIndex: Int) {
        draggedCornerIndex = cornerIndex
        draggedEdgeIndex = -1
    }

    fun startEdgeDrag(edgeIndex: Int) {
        draggedEdgeIndex = edgeIndex
        draggedCornerIndex = -1
    }

    fun endDrag() {
        draggedCornerIndex = -1
        draggedEdgeIndex = -1
    }

    fun isDragging(): Boolean = draggedCornerIndex >= 0 || draggedEdgeIndex >= 0
}
