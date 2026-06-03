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

import org.fairscan.app.domain.PageToExport
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

data class GeneratedPdf(
    val file: File,
    val sizeInBytes: Long,
    val pageCount: Int,
)

fun interface PdfWriter {
    suspend fun writePdfFromJpegs(
        pages: List<PageToExport>,
        outputStream: OutputStream,
        onProgress: (Int) -> Unit,
    )
}

class FileManager(
    private val pdfDir: File,
    private val externalDir: File,
    private val pdfWriter: PdfWriter
) {
    companion object {
        fun addPdfExtensionIfMissing(fileName: String): String {
            return if (fileName.lowercase().endsWith(".pdf"))
                fileName
            else
                "$fileName.pdf"
        }
    }

    suspend fun generatePdf(pages: List<PageToExport>, onProgress: (Int) -> Unit): GeneratedPdf {
        pdfDir.mkdirs()
        require(pdfDir.exists() && pdfDir.isDirectory) { "Invalid pdfDir: $pdfDir" }
        val file = File(pdfDir, "${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use {
            pdfWriter.writePdfFromJpegs(pages, it, onProgress)
        }
        val sizeBytes = file.length()
        return GeneratedPdf(file, sizeBytes, pages.size)
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
