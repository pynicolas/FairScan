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
package org.fairscan.evaluation

import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.isColoredDocument
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

fun main() {
    nu.pattern.OpenCV.loadLocally()
    ExportQualityEvaluator.runEvaluation()
}

object ExportQualityEvaluator {

    fun runEvaluation() {
        val root = File("evaluation")
        val datasetDir = File(root, "dataset")
        val imageDir = File(datasetDir, "images")
        val outputDir = File("evaluation/reports/export_quality").apply { mkdirs() }

        val imgFiles = imageDir.listFiles { f -> f.extension.lowercase() == "jpg" }
            ?.toList() ?: listOf<File>()

        val qualities = listOf(60, 75, 80)
        val maxPixelsList = listOf(1_500_000, 2_000_000, 4_000_000)

        for (imgFile in imgFiles) {
            val imgName = imgFile.nameWithoutExtension
            val maskFile = File(datasetDir, "masks/$imgName.png")
            if (!maskFile.exists()) continue

            val sourceMat = Imgcodecs.imread(imgFile.absolutePath)
            if (sourceMat.empty()) continue

            val maskMat = Imgcodecs.imread(maskFile.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
            if (maskMat.empty()) continue

            println("Processing ${imgName}...")

            val mask = MatMask(maskMat)

            val quad = detectDocumentQuad(mask, isLiveAnalysis = false)
                ?.scaledTo(mask.width, mask.height, sourceMat.width(), sourceMat.height())
            if (quad == null) {
                println("Failed to detect quad for $imgName")
                continue
            }

            val isColored = isColoredDocument(sourceMat, mask, quad)

            for (quality in qualities) {

                for (maxPixels in maxPixelsList) {
                    val outputMat =
                        extractDocument(sourceMat, quad, 0, isColored, maxPixels.toLong())

                    val outputFile = File(outputDir, "$imgName-$quality-$maxPixels.jpg")
                    val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality)
                    if (!Imgcodecs.imwrite(outputFile.absolutePath, outputMat, params)) {
                        throw RuntimeException("Could not write image to ${outputFile.absolutePath}")
                    }

                    params.release()
                    outputMat.release()
                }
            }
            sourceMat.release()
            generateHtmlReport(outputDir, imgName, qualities, maxPixelsList, crop = CropParams())
        }
    }

    data class CropParams(
        val centerX: Double = 0.5,
        val centerY: Double = 0.5,
        val size: Double = 0.5
    )

    fun generateHtmlReport(
        outputDir: File,
        imgName: String,
        qualities: List<Int>,
        maxPixelsList: List<Int>,
        crop: CropParams
    ) {
        val htmlFile = File(outputDir, "$imgName.html")

        htmlFile.writeText(
            """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>$imgName – Export quality comparison</title>
            <style>
                body {
                    font-family: sans-serif;
                }
                .controls {
                    margin-bottom: 1em;
                }
                .grid {
                    display: grid;
                    grid-template-columns: repeat(${maxPixelsList.size}, 1fr);
                    gap: 8px;
                }
                .cell {
                    border: 1px solid #ccc;
                    padding: 4px;
                    font-size: 12px;
                }
                .crop {
                    width: 600px;
                    height: 300px;
                    overflow: hidden;
                    border: 1px solid #000;
                }
                .crop img {
                    transform-origin: top left;
                }
            </style>
        </head>
        <body>

        <h1>$imgName</h1>

        <div class="controls">
            Center X <input type="range" id="cx" min="0" max="1" step="0.01" value="${crop.centerX}">
            Center Y <input type="range" id="cy" min="0" max="1" step="0.01" value="${crop.centerY}">
            Size <input type="range" id="size" min="0.05" max="0.5" step="0.01" value="${crop.size}">
        </div>

        ${qualities.joinToString("") { q ->
                """
            <div class="grid">
                ${
                    maxPixelsList.joinToString("") { mp ->
                        val fileName = "$imgName-$q-$mp.jpg"
                        """
                        <div class="cell">
                            <div>q$q - $mp px - ${File(outputDir, fileName).length() / 1024}kB</div>
                            <div class="crop">
                                <img src="$fileName" data-img>
                            </div>
                        </div>
                        """
                    }
                }
            </div>
            """
            }}

        <script>
            const imgs = document.querySelectorAll('[data-img]');
            const cxInput = document.getElementById('cx');
            const cyInput = document.getElementById('cy');
            const sizeInput = document.getElementById('size');

            function update() {
                const cx = parseFloat(cxInput.value);
                const cy = parseFloat(cyInput.value);
                const size = parseFloat(sizeInput.value);
            
                imgs.forEach(img => {
                    const crop = img.parentElement;
                    const cropSize = crop.clientWidth;
            
                    const iw = img.naturalWidth;
                    const ih = img.naturalHeight;
            
                    const minDim = Math.min(iw, ih);
            
                    const cropPixels = size * minDim;
            
                    const scale = cropSize / cropPixels;
            
                    const centerX = cx * iw;
                    const centerY = cy * ih;
            
                    const x0 = centerX - cropPixels / 2;
                    const y0 = centerY - cropPixels / 2;
            
                    img.style.transform =
                        `translate(${'$'}{-x0 * scale}px, ${'$'}{-y0 * scale}px) scale(${'$'}{scale})`;
                });
            }

            imgs.forEach(img => {
                if (img.complete) update();
                else img.onload = update;
            });

            cxInput.oninput = update;
            cyInput.oninput = update;
            sizeInput.oninput = update;
        </script>

        </body>
        </html>
        """.trimIndent()
        )
    }

}