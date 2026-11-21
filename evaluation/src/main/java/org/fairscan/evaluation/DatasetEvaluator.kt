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

import org.fairscan.imageprocessing.Mask
import org.fairscan.imageprocessing.Mode
import org.fairscan.imageprocessing.autoColorMode
import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.enhanceCapturedImage
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.scaledTo
import org.fairscan.imageprocessing.toImageSize
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

fun main() {
    nu.pattern.OpenCV.loadLocally()
    DatasetEvaluator.run()
}

class MatMask(private val mat: Mat) : Mask {
    override val width: Int get() = mat.width()
    override val height: Int get() = mat.height()

    override fun toMat(): Mat = mat.clone()
}

data class EvaluationImage(
    val image: Mat,
    val caption: String? = null
)

data class EvaluationResult(
    val images: List<EvaluationImage>,
    val description: String? = null,
)

fun interface DatasetEvaluation {
    fun evaluate(
        name: String,
        image: Mat,
        enhanced: Mat,
    ): EvaluationResult
}

object DatasetEvaluator {

    data class Entry(
        val name: String,
        val inputFile: File,
        val maskFile: File
    )

    fun run(
        evaluation: DatasetEvaluation = { name, _, enhanced ->
            println("Processing $name")
            EvaluationResult(listOf(EvaluationImage(enhanced, "end result")))
        },
        inputDir: File = File("evaluation/dataset/images"),
        outputDir: File = File("evaluation/reports/results")
    ) {
        val maskDir = File("evaluation/dataset/masks")
        outputDir.mkdirs()

        val entries = inputDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg") }
            ?.mapNotNull { img ->
                val mask = File(maskDir, img.nameWithoutExtension + ".png")
                if (mask.exists()) Entry(img.nameWithoutExtension, img, mask) else null
            }?.sortedBy { e -> e.name }
            ?: emptyList()

        val htmlFragments = mutableListOf<String>()

        for (entry in entries) {
            val inputMat = Imgcodecs.imread(entry.inputFile.absolutePath)
            if (inputMat.empty()) continue

            val maskMat = Imgcodecs.imread(entry.maskFile.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
            if (maskMat.empty()) {
                inputMat.release()
                continue
            }

            val mask = MatMask(maskMat)
            val quad = detectDocumentQuad(mask, inputMat.size().toImageSize(), Mode.CAPTURE)
                ?.scaledTo(mask.width, mask.height, inputMat.width(), inputMat.height())
            if (quad != null) {
                val maxPixels = 2_000_000L
                val colorMode = autoColorMode(inputMat, mask, quad)
                val cropped = extractDocument(inputMat, quad, 0, maxPixels, { i -> i })
                val enhanced = enhanceCapturedImage(cropped, colorMode, colorMode, maxPixels)
                val result = evaluation.evaluate(entry.name, cropped, enhanced)
                htmlFragments += renderEntry(entry.name, inputMat, result, outputDir)
                cropped.release()
            }
            inputMat.release()
            maskMat.release()
        }

        buildHtmlReport(htmlFragments)
    }

    private fun renderEntry(
        name: String,
        inputMat: Mat,
        result: EvaluationResult,
        outputDir: File
    ): String {
        val inputFileName = "${name}_input.jpg"
        Imgcodecs.imwrite(File(outputDir, inputFileName).absolutePath, inputMat)

        val outputImages = result.images.mapIndexed { index, image ->
            val fileName = "${name}_output$index.jpg"
            Imgcodecs.imwrite(File(outputDir, fileName).absolutePath, image.image)
            image.image.release()

            imgHtml(fileName, image.caption)
        }

        return """
            <div class="entry">
                <h3>$name</h3>
                ${result.description?.let { """<div class="description">$it</div>""" }.orEmpty()}
                <div class="row">
                    ${imgHtml(inputFileName, "captured")}
                    ${outputImages.joinToString("\n")}
                </div>
            </div>
        """.trimIndent()
        }

    private fun imgHtml(fileName: String, caption: String?) = """
        <figure class="cell">
            <figcaption>${caption.orEmpty()}</figcaption>
            <a href="results/$fileName"><img src="results/$fileName" /></a>
        </figure>
    """.trimIndent()

    private fun buildHtmlReport(parts: List<String>) {
        val css = """
           body { font-family: sans-serif; padding: 20px; }
          .entry { margin-bottom: 40px; }
          .description { margin-bottom: 12px; }
          .row { display: flex; flex-direction: row; align-items: flex-start; gap: 20px; }
          .cell { display: flex; flex-direction: column; margin: 0; width: 400px; flex-shrink: 0; }
          .cell img { max-width: 100%; height: auto; object-fit: contain; }
          .cell figcaption { margin-top: 6px; font-size: 12px; color: #555; text-align: center; min-height: 1.2em; }
        """
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8" />
                <title>Dataset Evaluation</title>
                <style>$css</style>
            </head>
            <body>
                <h1>Dataset Evaluation</h1>
                ${parts.joinToString("\n")}
            </body>
            </html>
        """.trimIndent()

        val file = File("evaluation/reports/index.html")
        file.writeText(html)
        println("Done! report at: $file")
    }
}
