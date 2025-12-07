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
package org.fairscan.evaluation

import org.fairscan.imageprocessing.Mask
import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

fun main() {
    nu.pattern.OpenCV.loadLocally()
    DatasetEvaluator.runDatasetEvaluation()
}

class MatMask(private val mat: Mat) : Mask {
    override val width: Int get() = mat.width()
    override val height: Int get() = mat.height()

    override fun toMat(): Mat = mat
}

object DatasetEvaluator {

    data class Entry(
        val name: String,
        val inputFile: File,
        val maskFile: File
    )

    fun runDatasetEvaluation() {
        val inputDir = File("evaluation/dataset/images")
        val maskDir = File("evaluation/dataset/masks")
        val outputDir = File("evaluation/reports/results").apply { mkdirs() }

        val entries = inputDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg") }
            ?.mapNotNull { img ->
                val mask = File(maskDir, img.nameWithoutExtension + ".png")
                if (mask.exists()) Entry(img.nameWithoutExtension, img, mask) else null
            }
            ?: emptyList()

        val htmlFragments = mutableListOf<String>()

        for (e in entries) {
            println("Processing ${e.name}...")

            val inputMat = Imgcodecs.imread(e.inputFile.absolutePath)
            if (inputMat.empty()) continue

            val maskMat = Imgcodecs.imread(e.maskFile.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
            if (maskMat.empty()) continue

            val mask = MatMask(maskMat)

            val quad = detectDocumentQuad(mask, isLiveAnalysis = false)
                ?.scaledTo(mask.width, mask.height, inputMat.width(), inputMat.height())

            val corrected: Mat? = if (quad != null) {
                extractDocument(inputMat, quad = quad, rotationDegrees = 0, mask)
            } else null

            val inputOut = File(outputDir, "${e.name}_input.jpg")
            Imgcodecs.imwrite(inputOut.absolutePath, inputMat)

            val outputOut = File(outputDir, "${e.name}_output.jpg")
            if (corrected != null) {
                Imgcodecs.imwrite(outputOut.absolutePath, corrected)
            }

            htmlFragments += """
                <div class="entry">
                    <h3>${e.name}</h3>
                    <div class="row">
                        <img src="results/${e.name}_input.jpg" />
                        <img src="results/${e.name}_output.jpg" />
                    </div>
                </div>
            """.trimIndent()
        }

        buildHtmlReport(htmlFragments)
        println("Done! report at: reports/index.html")
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
                    .row { display: flex; flex-direction: row; align-items: center; }
                    .entry { margin-bottom: 40px; }
                </style>
            </head>
            <body>
                <h1>Dataset Evaluation</h1>
                ${parts.joinToString("\n")}
            </body>
            </html>
        """.trimIndent()

        File("evaluation/reports/index.html").writeText(html)
    }
}
