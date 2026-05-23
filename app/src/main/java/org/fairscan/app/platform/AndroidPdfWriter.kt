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
package org.fairscan.app.platform

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.fairscan.app.BuildConfig
import org.fairscan.app.data.PdfWriter
import org.fairscan.app.domain.PageToExport
import org.fairscan.imageprocessing.EstimatedDimensions
import java.io.OutputStream
import java.util.Calendar

class AndroidPdfWriter : PdfWriter {
    override suspend fun writePdfFromJpegs(pages: List<PageToExport>, outputStream: OutputStream): Int {
        val doc = PDDocument()
        doc.documentInformation.creationDate = Calendar.getInstance()
        doc.documentInformation.creator = "FairScan ${BuildConfig.VERSION_NAME}"
        doc.use { document ->
            for (page in pages) {
                val image = JPEGFactory.createFromByteArray(document, page.jpeg.get().bytes)

                // PDF has 72 points (units) per inch, 1 inch = 25.4 mm
                val pointsPerMm = 72f / 25.4f

                val widthPx = image.width.toFloat()
                val heightPx = image.height.toFloat()

                val dimensions = page.estimatedDimensions()
                val (widthPoints, heightPoints) = when (dimensions) {
                    is EstimatedDimensions.Physical -> {
                        dimensions.widthMm.toFloat() * pointsPerMm to dimensions.heightMm.toFloat() * pointsPerMm
                    }
                    else -> {
                        // No physical dimensions available: approximate using US Letter max dimension
                        val maxDimInMm = 279.4f
                        val scalePxToMm = maxDimInMm / maxOf(widthPx, heightPx)
                        widthPx * scalePxToMm * pointsPerMm to heightPx * scalePxToMm * pointsPerMm
                    }
                }

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
