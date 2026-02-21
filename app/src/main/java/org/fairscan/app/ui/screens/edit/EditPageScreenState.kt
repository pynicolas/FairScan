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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Quad

class EditPageScreenState {
    companion object {
        val LIFT_WIGGLE_MAX_DISTANCE = 8.dp
        const val LIFT_WIGGLE_WINDOW_MS = 70L
    }

    var bitmap by mutableStateOf<android.graphics.Bitmap?>(null)
    var containerSize by mutableStateOf<IntSize?>(null)
    var editableQuad by mutableStateOf<Quad?>(null)
    var draggedCornerIndex by mutableIntStateOf(-1)
    var dragPosition by mutableStateOf<Offset?>(null)
    /** True from the moment the finger touches a drag handle until it is lifted. */
    var isTouching by mutableStateOf(false)
    /**
     * Corner / edge index detected at the raw touch-down (before touch-slop).
     * Carried into [startCornerDrag] so that the slop-adjusted
     * position in onDragStart cannot miss the handle.
     */
    var touchDownCornerIndex by mutableIntStateOf(-1)

    private var quadBeforeDrag: Quad? = null
    private var quadBeforeLastDragStep: Quad? = null
    private var lastDragStepDistancePx: Float = Float.MAX_VALUE
    private var lastDragStepAtMs: Long = 0L
    private var initialQuad: Quad? = null

    fun updateQuad(newQuad: Quad) {
        editableQuad = newQuad
    }

    fun startCornerDrag(cornerIndex: Int) {
        quadBeforeDrag = editableQuad
        draggedCornerIndex = cornerIndex
        clearLastDragStep()
    }

    fun recordDragStep(previousQuad: Quad, dragAmount: Offset, eventTimeMs: Long = System.currentTimeMillis()) {
        quadBeforeLastDragStep = previousQuad
        lastDragStepDistancePx = dragAmount.getDistance()
        lastDragStepAtMs = eventTimeMs
    }

    fun rollbackLastDragStepIfLikelyLiftWiggle(
        maxDistancePx: Float,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (quadBeforeLastDragStep == null) return
        val isRecent = nowMs - lastDragStepAtMs <= LIFT_WIGGLE_WINDOW_MS
        val isSmall = lastDragStepDistancePx <= maxDistancePx
        if (isRecent && isSmall) {
            editableQuad = quadBeforeLastDragStep
        }
    }

    fun endDrag() {
        quadBeforeDrag = null
        clearLastDragStep()
        draggedCornerIndex = -1
        // dragPosition is intentionally kept so the loupe can still render
        // during its 1-second fade-out after the finger is lifted.
    }

    private fun clearLastDragStep() {
        quadBeforeLastDragStep = null
        lastDragStepDistancePx = Float.MAX_VALUE
        lastDragStepAtMs = 0L
    }

    /**
     * Called as soon as the finger touches a drag handle (before touch-slop),
     * so the loupe is shown immediately.
     * [cornerIndex] is the handle index found at the exact
     * touch position; it is stored so that drag start handling can use it even
     * if the slop-adjusted position drifts outside the hit-test radius.
     */
    fun onTouchDown(position: Offset, cornerIndex: Int = -1) {
        isTouching = true
        dragPosition = position
        touchDownCornerIndex = cornerIndex
    }

    /** Called when the finger is lifted; triggers the loupe fade-out delay. */
    fun onTouchUp() {
        isTouching = false
        touchDownCornerIndex = -1
    }

    fun isDragging(): Boolean = draggedCornerIndex >= 0

    fun setInitialQuad(quad: Quad) {
        initialQuad = quad
        editableQuad = quad
    }

    fun hasUnsavedChanges(): Boolean {
        return editableQuad != initialQuad
    }

    fun revertToInitial() {
        editableQuad = initialQuad
    }
}
