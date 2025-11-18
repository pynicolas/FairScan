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
package org.fairscan.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.fairscan.app.R

@Composable
fun NewDocumentDialog(onConfirm: () -> Unit, showDialog: MutableState<Boolean>, title: String) {
    ConfirmationDialog(title, stringResource(R.string.new_document_warning), showDialog, onConfirm)
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    showDialog: MutableState<Boolean>,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                showDialog.value = false
                onConfirm()
            }) {
                Text(stringResource(R.string.yes), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { showDialog.value = false }) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
            }
        },
        onDismissRequest = { showDialog.value = false },
    )
}
