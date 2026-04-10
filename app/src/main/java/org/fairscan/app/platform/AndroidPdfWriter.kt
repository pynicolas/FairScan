/*
 * Copyright 2025-2026 Pierre-Yves Nicolas
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
package org.fairscan.app.platform

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.fairscan.app.BuildConfig
import org.fairscan.app.data.PdfWriter
import org.fairscan.app.domain.JpegProvider
import java.io.OutputStream
import java.util.Calendar

class AndroidPdfWriter : PdfWriter {
    override suspend fun writePdfFromJpegs(jpegs: List<JpegProvider>, outputStream: OutputStream): Int {
        val doc = PDDocument()
        doc.documentInformation.creationDate = Calendar.getInstance()
        doc.documentInformation.creator = "FairScan ${BuildConfig.VERSION_NAME}"
        doc.use { document ->
            for (jpegBytes in jpegs) {
                val image = JPEGFactory.createFromByteArray(document, jpegBytes.get().bytes)

                // Let's say that the physical dimensions of the page are close to US Letter
                // US Letter: 215.9×279.4 mm (A4: 210×297 mm)
                val maxDimInMm = 279.4f
                // PDF has 72 points (units) per inch, 1 inch = 25.4 mm
                val pointsPerMm = 72f / 25.4f

                val widthPx = image.width.toFloat()
                val heightPx = image.height.toFloat()

                val maxPx = maxOf(widthPx, heightPx)
                val scalePxToMm = maxDimInMm / maxPx

                val widthPoints = widthPx * scalePxToMm * pointsPerMm
                val heightPoints = heightPx * scalePxToMm * pointsPerMm

                val page = PDPage(PDRectangle(widthPoints, heightPoints))
                document.addPage(page)

                val contentStream = PDPageContentStream(document, page, AppendMode.OVERWRITE, false)
                contentStream.drawImage(image, 0f, 0f, widthPoints, heightPoints)
                contentStream.close()
            }
            // TODO So the whole document is in memory before this line...
            document.save(outputStream)
        }
        return doc.numberOfPages
    }
}
