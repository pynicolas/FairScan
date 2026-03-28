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

import androidx.compose.ui.geometry.Offset
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.Test

class EditPageScreenStateTest {

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
        val state = EditPageScreenState()

        assertThat(state.bitmap).isNull()
        assertThat(state.containerSize).isNull()
        assertThat(state.editableQuad).isNull()
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
        assertThat(state.draggedEdgeIndex).isEqualTo(-1)
        assertThat(state.isDragging()).isFalse()
        // Touch / loupe state
        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
        assertThat(state.dragPosition).isNull()
    }

    @Test
    fun quadUpdates_workCorrectly() {
        val state = EditPageScreenState()

        state.updateQuad(testQuad)
        assertThat(state.editableQuad).isEqualTo(testQuad)

        state.updateQuad(updatedQuad)
        assertThat(state.editableQuad).isEqualTo(updatedQuad)
    }

    @Test
    fun cornerAndEdgeDragging_managesStateCorrectly() {
        val state = EditPageScreenState()

        // Corner drag starts correctly
        for (i in 0 until 4) {
            state.startCornerDrag(i)
            assertThat(state.draggedCornerIndex).isEqualTo(i)
            assertThat(state.draggedEdgeIndex).isEqualTo(-1)
            assertThat(state.isDragging()).isTrue()
        }

        // Edge drag starts correctly and resets corner
        for (i in 0 until 4) {
            state.startEdgeDrag(i)
            assertThat(state.draggedEdgeIndex).isEqualTo(i)
            assertThat(state.draggedCornerIndex).isEqualTo(-1)
            assertThat(state.isDragging()).isTrue()
        }

        // Switching from corner to edge resets corner
        state.startCornerDrag(0)
        state.startEdgeDrag(2)
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
        assertThat(state.draggedEdgeIndex).isEqualTo(2)

        // Switching from edge to corner resets edge
        state.startEdgeDrag(2)
        state.startCornerDrag(0)
        assertThat(state.draggedEdgeIndex).isEqualTo(-1)
        assertThat(state.draggedCornerIndex).isEqualTo(0)

        // End drag resets all
        state.startCornerDrag(2)
        state.endDrag()
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
        assertThat(state.draggedEdgeIndex).isEqualTo(-1)
        assertThat(state.isDragging()).isFalse()

        // End drag after edge drag
        state.startEdgeDrag(1)
        state.endDrag()
        assertThat(state.isDragging()).isFalse()

        // End drag when not dragging stays in non-dragging state
        state.endDrag()
        assertThat(state.isDragging()).isFalse()
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
        assertThat(state.draggedEdgeIndex).isEqualTo(-1)
    }

    @Test
    fun fullDragCycle_preservesQuadAfterDragEnds() {
        val state = EditPageScreenState()

        // Corner drag cycle
        assertThat(state.isDragging()).isFalse()
        state.startCornerDrag(1)
        assertThat(state.isDragging()).isTrue()
        state.updateQuad(updatedQuad)
        assertThat(state.editableQuad).isEqualTo(updatedQuad)
        assertThat(state.isDragging()).isTrue()
        state.endDrag()
        assertThat(state.isDragging()).isFalse()
        assertThat(state.editableQuad).isEqualTo(updatedQuad)

        // Edge drag cycle
        state.startEdgeDrag(3)
        assertThat(state.isDragging()).isTrue()
        state.updateQuad(testQuad)
        assertThat(state.editableQuad).isEqualTo(testQuad)
        state.endDrag()
        assertThat(state.isDragging()).isFalse()
        assertThat(state.editableQuad).isEqualTo(testQuad)
    }

    // ── onTouchDown ──────────────────────────────────────────────────────────

    @Test
    fun onTouchDown_setsIsTouchingAndDragPosition() {
        val state = EditPageScreenState()
        val pos = Offset(100f, 200f)

        state.onTouchDown(pos)

        assertThat(state.isTouching).isTrue()
        assertThat(state.dragPosition).isEqualTo(pos)
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
    }

    @Test
    fun onTouchDown_withCornerIndex_storesCornerIndex() {
        val state = EditPageScreenState()

        state.onTouchDown(Offset(50f, 50f), cornerIndex = 2)

        assertThat(state.isTouching).isTrue()
        assertThat(state.touchDownCornerIndex).isEqualTo(2)
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
    }

    @Test
    fun onTouchDown_withEdgeIndex_storesEdgeIndex() {
        val state = EditPageScreenState()

        state.onTouchDown(Offset(50f, 50f), edgeIndex = 3)

        assertThat(state.isTouching).isTrue()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.touchDownEdgeIndex).isEqualTo(3)
    }

    @Test
    fun onTouchDown_overwritesPreviousTouchDown() {
        val state = EditPageScreenState()
        state.onTouchDown(Offset(10f, 10f), cornerIndex = 0)

        state.onTouchDown(Offset(50f, 50f), cornerIndex = 3)

        assertThat(state.dragPosition).isEqualTo(Offset(50f, 50f))
        assertThat(state.touchDownCornerIndex).isEqualTo(3)
    }

    // ── onTouchUp ────────────────────────────────────────────────────────────

    @Test
    fun onTouchUp_clearsIsTouchingAndTouchDownIndices() {
        val state = EditPageScreenState()
        state.onTouchDown(Offset(100f, 200f), cornerIndex = 1)

        state.onTouchUp()

        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
    }

    @Test
    fun onTouchUp_preservesDragPosition() {
        val state = EditPageScreenState()
        val pos = Offset(100f, 200f)
        state.onTouchDown(pos, cornerIndex = 1)

        state.onTouchUp()

        // dragPosition must survive so the loupe can still render during its fade-out delay.
        assertThat(state.dragPosition).isEqualTo(pos)
    }

    @Test
    fun onTouchUp_whenNotTouching_isIdempotent() {
        val state = EditPageScreenState()

        state.onTouchUp()

        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
    }

    // ── endDrag ──────────────────────────────────────────────────────────────

    @Test
    fun endDrag_preservesDragPosition() {
        val state = EditPageScreenState()
        state.setInitialQuad(testQuad)
        val pos = Offset(100f, 200f)
        state.onTouchDown(pos, cornerIndex = 0)
        state.startCornerDrag(0)
        state.updateQuad(updatedQuad)

        state.endDrag()

        // dragPosition must NOT be nulled so the loupe stays visible during the 1 s fade-out.
        assertThat(state.dragPosition).isEqualTo(pos)
        assertThat(state.draggedCornerIndex).isEqualTo(-1)
        assertThat(state.draggedEdgeIndex).isEqualTo(-1)
    }

    @Test
    fun endDrag_doesNotResetTouchDownIndices() {
        val state = EditPageScreenState()
        state.setInitialQuad(testQuad)
        state.onTouchDown(Offset(100f, 200f), cornerIndex = 2)
        state.startCornerDrag(2)

        state.endDrag()

        // touchDownCornerIndex is owned by onTouchUp(), not endDrag().
        assertThat(state.touchDownCornerIndex).isEqualTo(2)
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
    }

    // ── full interaction cycles ───────────────────────────────────────────────

    @Test
    fun tapCycle_leavesStateConsistent() {
        val state = EditPageScreenState()
        val pos = Offset(100f, 200f)

        state.onTouchDown(pos, cornerIndex = 3)
        assertThat(state.isTouching).isTrue()
        assertThat(state.touchDownCornerIndex).isEqualTo(3)

        state.onTouchUp()

        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownCornerIndex).isEqualTo(-1)
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
        assertThat(state.dragPosition).isEqualTo(pos)   // preserved for loupe fade-out
        assertThat(state.isDragging()).isFalse()
    }

    @Test
    fun dragCycle_corner_leavesStateConsistent() {
        val state = EditPageScreenState()
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
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
        assertThat(state.dragPosition).isEqualTo(pos)   // preserved for loupe fade-out
        assertThat(state.editableQuad).isEqualTo(updatedQuad)
        assertThat(state.history.canUndo).isTrue()
    }

    @Test
    fun dragCycle_edge_leavesStateConsistent() {
        val state = EditPageScreenState()
        state.setInitialQuad(testQuad)
        val pos = Offset(150f, 80f)

        state.onTouchDown(pos, edgeIndex = 0)
        state.startEdgeDrag(0)
        assertThat(state.isDragging()).isTrue()
        assertThat(state.touchDownEdgeIndex).isEqualTo(0)

        state.updateQuad(updatedQuad)
        state.endDrag()
        state.onTouchUp()

        assertThat(state.isDragging()).isFalse()
        assertThat(state.isTouching).isFalse()
        assertThat(state.touchDownEdgeIndex).isEqualTo(-1)
        assertThat(state.dragPosition).isEqualTo(pos)
    }

    @Test
    fun consecutiveTaps_eachSetsCorrectTouchDownIndex() {
        val state = EditPageScreenState()

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
