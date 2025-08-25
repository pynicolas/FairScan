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
import java.io.FileOutputStream
import java.io.OutputStream

fun interface PdfWriter {
    fun writePdfFromJpegs(jpegs: Sequence<ByteArray>, outputStream: OutputStream): Int
}

class PdfFileManager(
    private val pdfDir: File,
    private val externalDir: File,
    private val pdfWriter: PdfWriter
) {
    fun generatePdf(jpegs: Sequence<ByteArray>): GeneratedPdf {
        pdfDir.mkdirs()
        require(pdfDir.exists() && pdfDir.isDirectory) { "Invalid pdfDir: $pdfDir" }
        val file = File(pdfDir, "${System.currentTimeMillis()}.pdf")
        val pageCount = FileOutputStream(file).use {
            pdfWriter.writePdfFromJpegs(jpegs, it)
        }
        val sizeBytes = file.length()
        return GeneratedPdf(file, sizeBytes, pageCount)
    }

    fun copyToExternalDir(original: File): File {
        if (!externalDir.exists()) {
            externalDir.mkdirs()
        }
        require(externalDir.exists() && externalDir.isDirectory) { "Invalid externalDir: $pdfDir" }
        val desiredFile = File(externalDir, original.name)
        val targetFile = getAvailableFilename(desiredFile)
        original.copyTo(targetFile)
        return targetFile
    }

    private fun getAvailableFilename(desiredFile: File): File {
        var file = desiredFile
        val dir = desiredFile.parentFile
        val nameWithoutExtension = desiredFile.nameWithoutExtension
        val extension = desiredFile.extension
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${nameWithoutExtension}($counter).$extension")
            counter++
        }
        return file
    }

    fun cleanUpOldFiles(thresholdInMillis: Int) {
        val now = System.currentTimeMillis()
        pdfDir.listFiles { file -> now - file.lastModified() > thresholdInMillis }
            ?.forEach { file -> file.delete() }
    }
}
