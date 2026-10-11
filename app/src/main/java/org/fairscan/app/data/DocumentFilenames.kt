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
package org.fairscan.app.data

import java.io.File

fun safeDocumentName(name: String): String {
    val cleaned = name.trim()
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .trim('.', ' ')
        .ifBlank { "FairScan" }
    val extension = cleaned.substringAfterLast('.', "")
        .takeIf { it.lowercase() in setOf("pdf", "jpg", "jpeg") }
        ?.let { ".$it" }.orEmpty()
    var base = cleaned.removeSuffix(extension)
    // Android filesystems limit names in bytes; leave room for an extension and collision suffix.
    while (base.toByteArray(Charsets.UTF_8).size > 180) {
        base = base.substring(0, base.offsetByCodePoints(base.length, -1))
    }
    return base.ifBlank { "FairScan" } + extension
}

fun availableDocumentFile(directory: File, filename: String): File {
    val desired = File(directory, safeDocumentName(filename))
    var target = desired
    var suffix = 1
    while (target.exists()) {
        target = File(directory, "${desired.nameWithoutExtension} ($suffix).${desired.extension}")
        suffix++
    }
    return target
}
