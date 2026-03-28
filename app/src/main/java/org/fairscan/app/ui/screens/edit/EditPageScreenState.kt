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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Quad

class EditPageScreenState {
    var bitmap by mutableStateOf<android.graphics.Bitmap?>(null)
    var containerSize by mutableStateOf<IntSize?>(null)
    var editableQuad by mutableStateOf<Quad?>(null)
    var draggedCornerIndex by mutableIntStateOf(-1)
    var draggedEdgeIndex by mutableIntStateOf(-1)
    var dragPosition by mutableStateOf<Offset?>(null)
    /** True from the moment the finger touches a drag handle until it is lifted. */
    var isTouching by mutableStateOf(false)
    /**
     * Corner / edge index detected at the raw touch-down (before touch-slop).
     * Carried into [startCornerDrag] / [startEdgeDrag] so that the slop-adjusted
     * position in onDragStart cannot miss the handle.
     */
    var touchDownCornerIndex by mutableIntStateOf(-1)
    var touchDownEdgeIndex by mutableIntStateOf(-1)

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
        // dragPosition is intentionally kept so the loupe can still render
        // during its 1-second fade-out after the finger is lifted.
    }

    /**
     * Called as soon as the finger touches a drag handle (before touch-slop),
     * so the loupe is shown immediately.
     * [cornerIndex] / [edgeIndex] are the handle indices found at the exact
     * touch position; they are stored so that [onDragStart] can use them even
     * if the slop-adjusted position drifts outside the hit-test radius.
     */
    fun onTouchDown(position: Offset, cornerIndex: Int = -1, edgeIndex: Int = -1) {
        isTouching = true
        dragPosition = position
        touchDownCornerIndex = cornerIndex
        touchDownEdgeIndex = edgeIndex
    }

    /** Called when the finger is lifted; triggers the loupe fade-out delay. */
    fun onTouchUp() {
        isTouching = false
        touchDownCornerIndex = -1
        touchDownEdgeIndex = -1
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
