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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.io.OutputStream
import kotlin.io.path.createTempDirectory

class PdfFileManagerTest {

    val pdfDir: File = createTempDirectory().toFile()
    val externalDir: File = createTempDirectory().toFile()
    val dummyPdfWriter = PdfWriter { _,_ -> 42 }

    @Test
    fun copyToExternalDir() {
        val original = File(pdfDir, "f.pdf")
        original.writeText("original content")
        val f = File(externalDir, "f.pdf")
        assertThat(f).doesNotExist()

        val manager = PdfFileManager(pdfDir, externalDir, dummyPdfWriter)
        assertThat(manager.copyToExternalDir(original))
            .isEqualTo(f)
            .hasContent("original content")

        val f1 = File(externalDir, "f(1).pdf")
        val f2 = File(externalDir, "f(2).pdf")
        assertThat(f1).doesNotExist()
        assertThat(manager.copyToExternalDir(original)).isEqualTo(f1)
        assertThat(manager.copyToExternalDir(original)).isEqualTo(f2)
    }

    @Test
    fun cleanUpOldFiles() {
        val subDir = File(pdfDir,"subDir")
        val manager = PdfFileManager(subDir, externalDir, dummyPdfWriter)
        manager.cleanUpOldFiles(10)
        assertThat(subDir).doesNotExist()

        subDir.mkdirs()
        assertThat(subDir).exists()
        val file1 = File(subDir, "file1")
        file1.createNewFile()
        val file2 = File(subDir, "file2")
        file2.createNewFile()

        val now = System.currentTimeMillis()
        file1.setLastModified(now - 10_000)
        file2.setLastModified(now - 11_000)
        manager.cleanUpOldFiles(10_500)
        assertThat(file1).exists()
        assertThat(file2).doesNotExist()
    }

    @Test
    fun generatePdf() {
        val fakePdfWriter = object : PdfWriter {
            override fun writePdfFromJpegs(jpegs: Sequence<ByteArray>, outputStream: OutputStream): Int {
                val list = jpegs.toList()
                list.forEach { bytes -> outputStream.write(bytes) }
                return list.size
            }
        }
        val manager = PdfFileManager(pdfDir, externalDir, fakePdfWriter)
        val jpegs = listOf(byteArrayOf(0x01, 0x02), byteArrayOf(0x11)).asSequence()
        val pdf = manager.generatePdf(jpegs)
        assertThat(pdf.pageCount).isEqualTo(2)
        assertThat(pdf.sizeInBytes).isEqualTo(3)
        assertThat(pdf.file.readBytes()).isEqualTo(byteArrayOf(0x01, 0x02, 0x11))
        assertThat(pdf.file.name).endsWith(".pdf")
    }
}
