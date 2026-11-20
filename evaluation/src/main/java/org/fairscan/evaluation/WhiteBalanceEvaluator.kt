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

import org.fairscan.imageprocessing.WhiteBalanceAnalysis
import org.fairscan.imageprocessing.analyzeWhiteBalance
import org.fairscan.imageprocessing.enhanceColorImage
import org.opencv.core.Mat
import java.io.File
import java.util.Locale

fun main() {
    nu.pattern.OpenCV.loadLocally()
    DatasetEvaluator.run(
        WhiteBalanceEvaluator,
        inputDir = File("evaluation/dataset/images/wb"))
}

object WhiteBalanceEvaluator : DatasetEvaluation {

    override fun evaluate(name: String, image: Mat, enhanced: Mat): EvaluationResult {
        val wb = analyzeWhiteBalance(image)
        val balanced = wb.gains.applyTo(image)
        val description = describe(wb)
        println("$name | $description")
        return EvaluationResult(
            description = description,
            images = listOf(
                EvaluationImage(image, "cropped"),
                EvaluationImage(balanced, "white balance (strength ${f2(wb.strength)})"),
                EvaluationImage(enhanceColorImage(balanced, false), "end result"),
                EvaluationImage(enhanceColorImage(image, false), "result without white balance"),
            )
        )
    }

    private fun describe(m: WhiteBalanceAnalysis): String {
        val paper = m.paperColor
        fun pct(gain: Double) = String.format(Locale.ROOT, "%+.0f", (gain - 1.0) * 100)
        return "paper BGR (${f0(paper.b)}, ${f0(paper.g)}, ${f0(paper.r)})" +
                " | topChroma=${f1(m.topChroma)}" +
                " | strength=${f2(m.strength)}" +
                " | correction=${f1(m.gains.maxChange * 100)}%" +
                " (B${pct(m.gains.b)} G${pct(m.gains.g)} R${pct(m.gains.r)})"
    }

    private fun f0(x: Double) = String.format(Locale.ROOT, "%.0f", x)
    private fun f1(x: Double) = String.format(Locale.ROOT, "%.1f", x)
    private fun f2(x: Double) = String.format(Locale.ROOT, "%.2f", x)
}
