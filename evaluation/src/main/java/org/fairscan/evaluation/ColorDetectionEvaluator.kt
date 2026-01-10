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

import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.isColoredDocument
import org.fairscan.imageprocessing.scaledTo
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

fun main() {
    nu.pattern.OpenCV.loadLocally()
    ColorDetectionEvaluator.run()
}

object ColorDetectionEvaluator {

    fun run() {
        val root = File("evaluation")
        val datasetDir = File(root, "dataset")
        val metadataDir = File(datasetDir, "metadata")
        val outputDir = File("evaluation/reports/color_detection").apply { mkdirs() }

        val imageMetas = CsvMetadata.readImagesCsv(File(metadataDir, "images.csv"))
        val documentMetas = CsvMetadata.readDocumentsCsv(File(metadataDir, "documents.csv"))

        val results = mutableListOf<ColorResult>()
        var nbProcessedImages = 0

        for (meta in imageMetas) {
            val expected = documentMetas[meta.docId]?.isColored ?: continue
            val imgName = meta.imgName.removeSuffix(".jpg")
            val imgFile = File(datasetDir, "images/$imgName.jpg")
            val maskFile = File(datasetDir, "masks/$imgName.png")
            if (!imgFile.exists() || !maskFile.exists()) continue

            val mat = Imgcodecs.imread(imgFile.absolutePath)
            if (mat.empty()) continue

            val maskMat = Imgcodecs.imread(maskFile.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
            if (maskMat.empty()) continue

            println("Processing ${imgName}...")

            val mask = MatMask(maskMat)

            val quad = detectDocumentQuad(mask, isLiveAnalysis = false)
                ?.scaledTo(mask.width, mask.height, mat.width(), mat.height())

            if (quad == null) continue
            val isColored = isColoredDocument(mat, mask, quad)
            val extracted = extractDocument(mat, quad, 0, isColored, 2_000_000)

            val detected = isColored

            nbProcessedImages++

            val inputOut = File(outputDir, "${imgName}_input.jpg")
            Imgcodecs.imwrite(inputOut.absolutePath, mat)

            val outputOut = File(outputDir, "${imgName}_output.jpg")
            Imgcodecs.imwrite(outputOut.absolutePath, extracted)

            results += ColorResult(
                imgName,
                originalFile = inputOut,
                documentFile = outputOut,
                colorCase = ColorCase(expected, detected),
            )
        }

        ColorDetectionReport.writeHtml(
            File(outputDir, "index.html"),
            Score(results.groupingBy { it.colorCase }.eachCount()),
            results
        )
    }
}

data class ColorCase(
    val expected: Boolean,
    val detected: Boolean
) {
    val isMismatch: Boolean get() = expected != detected
}

data class ColorResult(
    val imgName: String,
    val originalFile: File,
    val documentFile: File,
    val colorCase: ColorCase
)

data class Score(
    val byCase: Map<ColorCase, Int>
) {
    val total: Int get() = byCase.values.sum()
    val mismatchCount: Int get() = byCase.filter { it.key.isMismatch }.values.sum()
    val accuracy: Double get() = 1.0 - mismatchCount.toDouble() / total
}

object ColorDetectionReport {

    fun writeHtml(output: File, score: Score, results: List<ColorResult>) {
        val sb = StringBuilder()

        sb.append("<html><body>")
        sb.append("<h1>Color Detection Evaluation</h1>")
        sb.append("<p>Total: ${score.total}</p>")
        sb.append("<p>Mismatches: ${score.mismatchCount}</p>")
        sb.append("<p>Accuracy: ${"%.2f".format(score.accuracy * 100)}%</p>")

        score.byCase.forEach { (case, count) ->
            sb.append("<p>expectedColor=${case.expected} / detectedColor=${case.detected} : $count</p>")
        }

        for (c in listOf(ColorCase(true, false), ColorCase(false, true))) {
            sb.append("<h2>expectedColor=${c.expected}  / detectedColor=${c.detected}</h2>")
            for (r in results.filter { it.colorCase == c }) {
                sb.append(
                    """
                <div style="margin-bottom:20px;">
                    <div style="display:flex; gap:20px;">
                        <div><img width="300" src="${r.originalFile.name}" /></div>
                        <div><img width="300" src="${r.documentFile.name}" /></div>
                    </div>
                </div>
            """.trimIndent()
                )
            }
        }

        sb.append("</body></html>")
        output.writeText(sb.toString())
    }
}


