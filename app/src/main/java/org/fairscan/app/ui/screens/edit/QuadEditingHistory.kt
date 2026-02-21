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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fairscan.imageprocessing.Quad

class QuadEditingHistory {
    private val undoStack = mutableListOf<Quad>()
    private val redoStack = mutableListOf<Quad>()

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    fun pushState(quad: Quad) {
        undoStack.add(quad)
        redoStack.clear()
        updateState()
    }

    fun undo(currentQuad: Quad): Quad? {
        if (undoStack.isEmpty()) return null

        redoStack.add(currentQuad)
        val previousState = undoStack.removeAt(undoStack.lastIndex)
        updateState()
        return previousState
    }

    fun redo(currentQuad: Quad): Quad? {
        if (redoStack.isEmpty()) return null

        undoStack.add(currentQuad)
        val nextState = redoStack.removeAt(redoStack.lastIndex)
        updateState()
        return nextState
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    private fun updateState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}
