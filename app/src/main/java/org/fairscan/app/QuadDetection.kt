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
package org.fairscan.app

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class QuadDetectionService(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = FileUtil.loadMappedFile(context, "fairscan-quadrilateral.tflite")
        interpreter = Interpreter(model)
    }

    fun detectQuadrilateral(inputMask: FloatArray): Quad {
        val width = 256
        val height = 256

        val inputTensor = TensorBuffer.createFixedSize(intArrayOf(1, height, width, 1), DataType.FLOAT32)
        inputTensor.loadArray(inputMask)
        val outputBuffer = Array(1) { FloatArray(8) } // 4 points (x, y) normalized in [0, 1]
        interpreter.run(inputTensor.buffer, outputBuffer)

        val vertices = outputBuffer[0]
            .toList()
            .chunked(2)
            .map { (x, y) -> Point(x.toDouble() * width, y.toDouble() * height) }
        return createQuad(vertices)
    }
}
