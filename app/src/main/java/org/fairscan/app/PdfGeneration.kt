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

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.OutputStream

class AndroidPdfWriter : PdfWriter {
    override fun writePdfFromJpegs(jpegs: Sequence<ByteArray>, outputStream: OutputStream): Int {
        val doc = PDDocument()
        doc.use { document ->
            for (jpegBytes in jpegs) {
                val image = JPEGFactory.createFromByteArray(document, jpegBytes)
                val page = PDPage(PDRectangle(image.width.toFloat(), image.height.toFloat()))
                document.addPage(page)
                val contentStream = PDPageContentStream(document, page, AppendMode.OVERWRITE, false)
                contentStream.drawImage(image, 0f, 0f)
                contentStream.close()
            }
            // TODO So the whole document is in memory before this line...
            document.save(outputStream)
        }
        return doc.numberOfPages
    }
}
