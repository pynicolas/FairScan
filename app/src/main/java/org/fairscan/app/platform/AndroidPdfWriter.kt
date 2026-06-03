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

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import org.fairscan.app.BuildConfig
import org.fairscan.app.data.PdfWriter
import org.fairscan.app.domain.PageToExport
import org.fairscan.app.domain.OcrService
import org.fairscan.imageprocessing.EstimatedDimensions
import org.fairscan.imageprocessing.OcrCoordinateConverter
import org.fairscan.imageprocessing.PaperFormats
import java.io.OutputStream
import java.util.Calendar

class AndroidPdfWriter(val ocrService: OcrService) : PdfWriter {
    override suspend fun writePdfFromJpegs(
        pages: List<PageToExport>,
        outputStream: OutputStream,
        onProgress: (Int) -> Unit,
    ) {
        val doc = PDDocument()
        doc.documentInformation.creationDate = Calendar.getInstance()
        doc.documentInformation.creator = "FairScan ${BuildConfig.VERSION_NAME}"
        doc.use { document ->
            for ((index, page) in pages.withIndex()) {
                val jpeg = page.jpeg.get()
                val image = JPEGFactory.createFromByteArray(document, jpeg.bytes)

                // PDF has 72 points (units) per inch, 1 inch = 25.4 mm
                val pointsPerMm = 72f / 25.4f

                val widthPx = image.width.toFloat()
                val heightPx = image.height.toFloat()

                val dimensions = page.estimatedDimensions()
                val (widthMm, heightMm) = when (dimensions) {
                    is EstimatedDimensions.Physical ->
                        constrainToMaxFormat(dimensions.widthMm, dimensions.heightMm)
                    else -> {
                        // No physical dimensions available
                        val maxDimMm = PaperFormats.A4.heightMm
                        val scalePxToMm = maxDimMm / maxOf(widthPx, heightPx)
                        constrainToMaxFormat(widthPx * scalePxToMm, heightPx * scalePxToMm)
                    }
                }
                val widthPoints = widthMm.toFloat() * pointsPerMm
                val heightPoints = heightMm.toFloat() * pointsPerMm

                val page = PDPage(PDRectangle(widthPoints, heightPoints))
                document.addPage(page)

                val contentStream = PDPageContentStream(document, page, AppendMode.OVERWRITE, false)
                contentStream.drawImage(image, 0f, 0f, widthPoints, heightPoints)

                createText(jpeg.toBitmap(), image, widthPoints, heightPoints, contentStream)

                contentStream.close()

                onProgress(index + 1)
            }
            // TODO So the whole document is in memory before this line...
            document.save(outputStream)
        }
    }

    private suspend fun createText(
        bitmap: Bitmap,
        image: PDImageXObject,
        widthPoints: Float,
        heightPoints: Float,
        contentStream: PDPageContentStream,
    ) {
        val ocr = ocrService.runOcr(bitmap)
        val ocrConverter = OcrCoordinateConverter(
            imageWidth = image.width,
            imageHeight = image.height,
            pageWidth = widthPoints,
            pageHeight = heightPoints
        )
        val font = PDType1Font.HELVETICA
        for (textBox in ocr) {
            val pdfRect = ocrConverter.convert(textBox.box)
            val fontSize = pdfRect.height * 0.8f
            contentStream.beginText()
            contentStream.setFont(font, fontSize)
            contentStream.setRenderingMode(RenderingMode.NEITHER)
            contentStream.newLineAtOffset(pdfRect.x, pdfRect.y)
            contentStream.showText(textBox.text)
            contentStream.endText()
        }
    }
}

fun constrainToMaxFormat(widthMm: Double, heightMm: Double): Pair<Double, Double> {
    val maxDim = 297.0   // A4 height
    val minDim = 215.9   // Letter width

    val scale = minOf(
        maxDim / maxOf(widthMm, heightMm),
        minDim / minOf(widthMm, heightMm),
        1.0
    )
    return widthMm * scale to heightMm * scale
}
