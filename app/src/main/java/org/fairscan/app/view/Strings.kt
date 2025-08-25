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
package org.fairscan.app.view

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.fairscan.app.R
import java.util.Date

@Composable
fun pageCountText(quantity: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(R.plurals.page_count, quantity, quantity)
}

fun formatDate(timestamp: Long, context: Context): String {
    val date = Date(timestamp)
    return DateFormat.getMediumDateFormat(context).format(date) + " " +
            DateFormat.getTimeFormat(context).format(date)
}
