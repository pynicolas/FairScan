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
package org.fairscan.app.data

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LogRepository(private val file: File) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun getLogs(): String = if (file.exists()) file.readText() else ""

    fun log(tag: String, message: String, throwable: Throwable) {
        val timestamp = formatter.format(Instant.now())

        val line = buildString {
            append("$timestamp [$tag] $message\n")
            append(throwable.stackTraceToString())
        }

        try {
            ensureFileSizeIsReasonable()
            file.appendText(line + "\n\n")
        } catch (_: Exception) {
            // Avoid throwing another exception: do nothing
        }
    }

    private fun ensureFileSizeIsReasonable() {
        if (file.length() > 128 * 1024) {
            file.writeText("")
        }
    }
}
