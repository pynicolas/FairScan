/*
 * Copyright 2025-2026 The FairScan authors
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
package org.fairscan.app.ui.screens.crop

import androidx.compose.ui.geometry.Offset
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.Test

class EditPageScreenStateTest {

    companion object {
        private const val wiggleThresholdPx = 8f
    }

    private val testQuad = Quad(
        topLeft = Point(0.1, 0.1),
        topRight = Point(0.9, 0.1),
        bottomRight = Point(0.9, 0.9),
        bottomLeft = Point(0.1, 0.9)
    )

    private val updatedQuad = Quad(
        topLeft = Point(0.2, 0.2),
        topRight = Point(0.8, 0.2),
        bottomRight = Point(0.8, 0.8),
        bottomLeft = Point(0.2, 0.8)
    )

    @Test
    fun initialState_hasCorrectDefaults() {
        val state = CropScreenState()

        assertThat(state.containerSize).isNull()
        assertThat(state.editableQuad).isNull()
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
        assertThat(state.isDragging()).isFalse()
        // Touch / loupe state
        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.dragPosition).isNull()
    }

    @Test
    fun quadUpdates_workCorrectly() {
        val state = CropScreenState()

        state.updateQuad(testQuad)
        assertThat(state.editableQuad).isEqualTo(testQuad)

        state.updateQuad(updatedQuad)
        assertThat(state.editableQuad).isEqualTo(updatedQuad)
    }

    @Test
    fun cornerDragging_managesStateCorrectly() {
        val state = CropScreenState()

        // Corner drag starts correctly
        for (i in 0 until 4) {
            state.startCornerDrag(i)
            assertThat(state.draggedCornerIndex).isEqualTo(i)
            assertThat(state.isDragging()).isTrue()
        }

        // End drag resets all
        state.startCornerDrag(2)
        state.endDrag()
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
        assertThat(state.isDragging()).isFalse()

        // End drag when not dragging stays in non-dragging state
        state.endDrag()
        assertThat(state.isDragging()).isFalse()
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
    }

    @Test
    fun fullDragCycle_preservesQuadAfterDragEnds() {
        val state = CropScreenState()

        assertThat(state.isDragging()).isFalse()
        state.startCornerDrag(1)
        assertThat(state.isDragging()).isTrue()
        state.updateQuad(updatedQuad)
        assertThat(state.editableQuad).isEqualTo(updatedQuad)
        assertThat(state.isDragging()).isTrue()
        state.endDrag()
        assertThat(state.isDragging()).isFalse()
        assertThat(state.editableQuad).isEqualTo(updatedQuad)
    }

    // ── onTouchDown ──────────────────────────────────────────────────────────

    @Test
    fun onTouchDown_setsIsTouchingAndDragPosition() {
        val state = CropScreenState()
        val pos = Offset(100f, 200f)

        state.onTouchDown(pos)

        assertThat(state.isTouching).isTrue()
        assertThat(state.dragPosition).isEqualTo(pos)
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
    }

    @Test
    fun onTouchDown_withCornerIndex_storesCornerIndex() {
        val state = CropScreenState()

        state.onTouchDown(Offset(50f, 50f), cornerIndex = 2)

        assertThat(state.isTouching).isTrue()
        assertThat(state.touchDownCornerIndex).isEqualTo(2)
    }

    @Test
    fun onTouchDown_withEdgeIndex_storesEdgeIndex() {
        // Edge index no longer exists; onTouchDown with no corner index leaves touchDownCornerIndex as -1.
        val state = CropScreenState()

        state.onTouchDown(Offset(50f, 50f))

        assertThat(state.isTouching).isTrue()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
    }

    @Test
    fun onTouchDown_overwritesPreviousTouchDown() {
        val state = CropScreenState()
        state.onTouchDown(Offset(10f, 10f), cornerIndex = 0)

        state.onTouchDown(Offset(50f, 50f), cornerIndex = 3)

        assertThat(state.dragPosition).isEqualTo(Offset(50f, 50f))
        assertThat(state.touchDownCornerIndex).isEqualTo(3)
    }

    // ── onTouchUp ────────────────────────────────────────────────────────────

    @Test
    fun onTouchUp_clearsIsTouchingAndTouchDownIndices() {
        val state = CropScreenState()
        state.onTouchDown(Offset(100f, 200f), cornerIndex = 1)

        state.onTouchUp()

        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
    }

    @Test
    fun onTouchUp_preservesDragPosition() {
        val state = CropScreenState()
        val pos = Offset(100f, 200f)
        state.onTouchDown(pos, cornerIndex = 1)

        state.onTouchUp()

        // dragPosition must survive so the loupe can still render during its fade-out delay.
        assertThat(state.dragPosition).isEqualTo(pos)
    }

    @Test
    fun onTouchUp_whenNotTouching_isIdempotent() {
        val state = CropScreenState()

        state.onTouchUp()

        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
    }

    // ── endDrag ──────────────────────────────────────────────────────────────

    @Test
    fun endDrag_preservesDragPosition() {
        val state = CropScreenState()
        state.setInitialQuad(testQuad)
        val pos = Offset(100f, 200f)
        state.onTouchDown(pos, cornerIndex = 0)
        state.startCornerDrag(0)
        state.updateQuad(updatedQuad)

        state.endDrag()

        // dragPosition must NOT be nulled so the loupe stays visible during the 1 s fade-out.
        assertThat(state.dragPosition).isEqualTo(pos)
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
    }

    @Test
    fun endDrag_doesNotResetTouchDownIndices() {
        val state = CropScreenState()
        state.setInitialQuad(testQuad)
        state.onTouchDown(Offset(100f, 200f), cornerIndex = 2)
        state.startCornerDrag(2)

        state.endDrag()

        // touchDownCornerIndex is owned by onTouchUp(), not endDrag().
        assertThat(state.touchDownCornerIndex).isEqualTo(2)
    }

    @Test
    fun rollbackLastDragStepIfLikelyLiftWiggle_revertsRecentSmallStep() {
        val state = CropScreenState()
        state.updateQuad(testQuad)
        state.startCornerDrag(0)

        state.recordDragStep(testQuad, Offset(3f, 2f), eventTimeMs = 1_000)
        state.updateQuad(updatedQuad)

        state.rollbackLastDragStepIfLikelyLiftWiggle(wiggleThresholdPx, nowMs = 1_030)

        assertThat(state.editableQuad).isEqualTo(testQuad)
    }

    @Test
    fun rollbackLastDragStepIfLikelyLiftWiggle_keepsLargeStep() {
        val state = CropScreenState()
        state.updateQuad(testQuad)
        state.startCornerDrag(0)

        state.recordDragStep(testQuad, Offset(20f, 0f), eventTimeMs = 1_000)
        state.updateQuad(updatedQuad)

        state.rollbackLastDragStepIfLikelyLiftWiggle(wiggleThresholdPx, nowMs = 1_030)

        assertThat(state.editableQuad).isEqualTo(updatedQuad)
    }

    @Test
    fun rollbackLastDragStepIfLikelyLiftWiggle_keepsOldSmallStep() {
        val state = CropScreenState()
        state.updateQuad(testQuad)
        state.startCornerDrag(0)

        state.recordDragStep(testQuad, Offset(3f, 2f), eventTimeMs = 1_000)
        state.updateQuad(updatedQuad)

        state.rollbackLastDragStepIfLikelyLiftWiggle(wiggleThresholdPx, nowMs = 1_200)

        assertThat(state.editableQuad).isEqualTo(updatedQuad)
    }

    @Test
    fun endDrag_clearsLastDragStepTracking() {
        val state = CropScreenState()
        state.updateQuad(testQuad)
        state.startCornerDrag(0)

        state.recordDragStep(testQuad, Offset(3f, 2f), eventTimeMs = 1_000)
        state.updateQuad(updatedQuad)
        state.endDrag()

        state.rollbackLastDragStepIfLikelyLiftWiggle(wiggleThresholdPx, nowMs = 1_010)

        assertThat(state.editableQuad).isEqualTo(updatedQuad)
    }

    // ── full interaction cycles ───────────────────────────────────────────────

    @Test
    fun tapCycle_leavesStateConsistent() {
        val state = CropScreenState()
        val pos = Offset(100f, 200f)

        state.onTouchDown(pos, cornerIndex = 3)
        assertThat(state.isTouching).isTrue()
        assertThat(state.touchDownCornerIndex).isEqualTo(3)

        state.onTouchUp()

        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.dragPosition).isEqualTo(pos)   // preserved for loupe fade-out
        assertThat(state.isDragging()).isFalse()
    }

    @Test
    fun dragCycle_corner_leavesStateConsistent() {
        val state = CropScreenState()
        state.setInitialQuad(testQuad)
        val pos = Offset(100f, 200f)

        state.onTouchDown(pos, cornerIndex = 1)
        state.startCornerDrag(1)
        assertThat(state.isDragging()).isTrue()
        assertThat(state.isTouching).isTrue()
        assertThat(state.draggedCornerIndex).isEqualTo(1)
        assertThat(state.touchDownCornerIndex).isEqualTo(1)

        state.updateQuad(updatedQuad)
        state.endDrag()
        state.onTouchUp()

        assertThat(state.isDragging()).isFalse()
        assertThat(state.isTouching).isFalse()
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.dragPosition).isEqualTo(pos)   // preserved for loupe fade-out
        assertThat(state.editableQuad).isEqualTo(updatedQuad)
    }

    @Test
    fun dragCycle_edge_leavesStateConsistent() {
        // Edge dragging is no longer supported; this test verifies that a touch
        // without a valid corner index simply does not trigger a drag.
        val state = CropScreenState()
        state.setInitialQuad(testQuad)
        val pos = Offset(150f, 80f)

        state.onTouchDown(pos)
        assertThat(state.isDragging()).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)

        state.onTouchUp()

        assertThat(state.isDragging()).isFalse()
        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.dragPosition).isEqualTo(pos)
    }

    @Test
    fun consecutiveTaps_eachSetsCorrectTouchDownIndex() {
        val state = CropScreenState()

        state.onTouchDown(Offset(10f, 10f), cornerIndex = 0)
        assertThat(state.touchDownCornerIndex).isEqualTo(0)
        state.onTouchUp()

        state.onTouchDown(Offset(90f, 10f), cornerIndex = 1)
        assertThat(state.touchDownCornerIndex).isEqualTo(1)
        state.onTouchUp()

        state.onTouchDown(Offset(90f, 90f), cornerIndex = 2)
        assertThat(state.touchDownCornerIndex).isEqualTo(2)
        state.onTouchUp()
    }
}
