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
package org.fairscan.app

import java.io.File

const val SCAN_DIR_NAME = "scanned_pages"

class ImageRepository(appFilesDir: File) {

    private val scanDir: File = File(appFilesDir, SCAN_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    val fileNames = scanDir.listFiles()
        ?.map { f -> f.name }?.toMutableList()
        ?:mutableListOf()

    fun imageIds(): List<String> {
        return fileNames.toList()
    }

    fun add(bytes: ByteArray) {
        val fileName = "${System.currentTimeMillis()}.jpg"
        val file = File(scanDir, fileName)
        file.writeBytes(bytes)
        fileNames.add(fileName)
    }

    fun getContent(id: String): ByteArray? {
        if (fileNames.contains(id)) {
            val file = File(scanDir, id)
            return file.readBytes()
        }
        return null
    }

    fun delete(id: String) {
        val file = File(scanDir, id)
        file.delete()
        fileNames.remove(id)
    }

    fun clear() {
        fileNames.clear()
        scanDir.listFiles()?.forEach {
            file -> file.delete()
        }
    }
}
