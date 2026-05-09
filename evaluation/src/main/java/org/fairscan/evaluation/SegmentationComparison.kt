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
package org.fairscan.evaluation

import nu.pattern.OpenCV
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

fun main() {
    OpenCV.loadLocally()
    SegmentationComparison.runEvaluation()
}

object SegmentationComparison {

    data class Entry(
        val name: String,
        val inputFile: File,
        val maskA: File,
        val maskB: File
    )

    fun runEvaluation() {
        val inputDir = File("evaluation/dataset/images/val-dataset-v2.1")
        val maskDirA = File("evaluation/dataset/masks/v1.1.0")
        val maskDirB = File("evaluation/dataset/masks/v1.2.0")
        val outputDir = File("evaluation/reports/results").apply { mkdirs() }

        val entries = inputDir
            .listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg") }
            ?.mapNotNull { img ->
                val maskA = File(maskDirA, img.nameWithoutExtension + ".png")
                val maskB = File(maskDirB, img.nameWithoutExtension + ".png")
                if (maskA.exists() && maskB.exists()) {
                    Entry(img.nameWithoutExtension, img, maskA, maskB)
                } else null
            } ?: emptyList()

        val htmlFragments = mutableListOf<String>()
        for (e in entries) {
            println("Processing ${e.name}...")

            val inputMat = Imgcodecs.imread(e.inputFile.absolutePath)
            if (inputMat.empty()) continue

            val inputOut = File(outputDir, "${e.name}_input.jpg")
            Imgcodecs.imwrite(inputOut.absolutePath, inputMat)

            val outA = File(outputDir, "${e.name}_modelA.jpg")
            val outB = File(outputDir, "${e.name}_modelB.jpg")

            renderOverlay(inputMat, e.maskA, outA)
            renderOverlay(inputMat, e.maskB, outB)

            htmlFragments += """
                <div class="entry">
                    <h3>${e.name}</h3>
                    <div class="row">
                        <div>
                            <img src="results/${e.name}_input.jpg" />
                        </div>
                        <div>
                            <img src="results/${e.name}_modelA.jpg" />
                        </div>
                        <div>
                            <img src="results/${e.name}_modelB.jpg" />
                        </div>
                    </div>
                </div>
            """.trimIndent()
        }
        buildHtmlReport(htmlFragments)
    }
}

private fun buildHtmlReport(parts: List<String>) {
    val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8" />
                <title>Dataset Evaluation</title>
                <style>
                    body { font-family: sans-serif; padding: 20px; }
                    img { max-width: 400px; margin-right: 20px; }
                    .row { display: flex; gap: 20px;align-items: flex-start; } 
                    .entry { margin-bottom: 40px; }
                </style>
            </head>
            <body>
                <h1>Dataset Evaluation</h1>
                ${parts.joinToString("\n")}
            </body>
            </html>
        """.trimIndent()

    File("evaluation/reports/segmentation-comparison.html").writeText(html)
}

private fun renderOverlay(
    inputMat: Mat,
    maskFile: File,
    outputFile: File
) {
    val maskMat = Imgcodecs.imread(maskFile.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
    if (maskMat.empty()) return

    val outputMat = Mat()
    overlayMask(inputMat, outputMat, maskMat)
    Imgcodecs.imwrite(outputFile.absolutePath, outputMat)
}