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

import nu.pattern.OpenCV
import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.scaledTo
import org.fairscan.imageprocessing.toCv
import org.fairscan.imageprocessing.toImageSize
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

fun main() {
    OpenCV.loadLocally()
    QuadDetectionEvaluator.runEvaluation()
}

object QuadDetectionEvaluator {

    data class Entry(
        val name: String,
        val inputFile: File,
        val maskFile: File
    )

    fun runEvaluation() {
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

            val originalSize = inputMat.size().toImageSize()
            val quad = detectDocumentQuad(mask, originalSize, isLiveAnalysis = false)
                ?.scaledTo(mask.width, mask.height, inputMat.width(), inputMat.height())

            val inputOut = File(outputDir, "${e.name}_input.jpg")
            Imgcodecs.imwrite(inputOut.absolutePath, inputMat)

            val outputMat = Mat()
            overlayMask(inputMat, outputMat, maskMat)

            if (quad != null) {
                val quadVertices: List<org.opencv.core.Point> =
                    listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
                        .map { it.toCv() }
                drawPolygon(quadVertices, outputMat)
            }
            val outputOut = File(outputDir, "${e.name}_output.jpg")
            Imgcodecs.imwrite(outputOut.absolutePath, outputMat)

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

    private fun drawPolygon(points: List<org.opencv.core.Point>, outputMat: Mat) {
        // Draw edges
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            Imgproc.line(
                outputMat,
                p1,
                p2,
                Scalar(255.0, 0.0, 0.0),
                3
            )
        }

        // Draw corners
        for (p in points) {
            Imgproc.circle(
                outputMat,
                p,
                6,
                Scalar(0.0, 255.0, 255.0),
                -1
            )
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

fun overlayMask(inputMat: Mat, outputMat: Mat, maskMat: Mat) {
    val resizedMask = Mat()
    Imgproc.resize(maskMat, resizedMask, inputMat.size(), 0.0, 0.0, Imgproc.INTER_NEAREST)

    Core.convertScaleAbs(inputMat, outputMat, 1.0, -80.0)

    val maskGray = Mat()
    if (resizedMask.channels() == 1) {
        resizedMask.copyTo(maskGray)
    } else {
        Imgproc.cvtColor(resizedMask, maskGray, Imgproc.COLOR_BGR2GRAY)
    }

    Core.normalize(maskGray, maskGray, 0.0, 255.0, Core.NORM_MINMAX)

    val maskColor = Mat.zeros(outputMat.size(), outputMat.type())
    maskColor.setTo(Scalar(0.0, 255.0, 0.0), maskGray)

    val alpha = 0.6
    Core.addWeighted(maskColor, alpha, outputMat, 1.0, 0.0, outputMat)
}
