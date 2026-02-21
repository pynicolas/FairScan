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

    val history = QuadEditingHistory()
    private var quadBeforeDrag: Quad? = null
    private var initialQuad: Quad? = null

    fun updateQuad(newQuad: Quad) {
        editableQuad = newQuad
    }

    fun startCornerDrag(cornerIndex: Int) {
        quadBeforeDrag = editableQuad
        draggedCornerIndex = cornerIndex
        draggedEdgeIndex = -1
    }

    fun startEdgeDrag(edgeIndex: Int) {
        quadBeforeDrag = editableQuad
        draggedEdgeIndex = edgeIndex
        draggedCornerIndex = -1
    }

    fun endDrag() {
        // Push state to history when drag ends (if quad changed)
        quadBeforeDrag?.let { before ->
            editableQuad?.let { after ->
                if (before != after) {
                    history.pushState(before)
                }
            }
        }
        quadBeforeDrag = null
        draggedCornerIndex = -1
        draggedEdgeIndex = -1
    }

    fun undo() {
        editableQuad?.let { current ->
            history.undo(current)?.let { previous ->
                editableQuad = previous
            }
        }
    }

    fun redo() {
        editableQuad?.let { current ->
            history.redo(current)?.let { next ->
                editableQuad = next
            }
        }
    }

    fun isDragging(): Boolean = draggedCornerIndex >= 0 || draggedEdgeIndex >= 0

    fun setInitialQuad(quad: Quad) {
        initialQuad = quad
        editableQuad = quad
    }

    fun hasUnsavedChanges(): Boolean {
        return editableQuad != initialQuad || history.canUndo
    }

    fun revertToInitial() {
        editableQuad = initialQuad
        history.clear()
    }
}
