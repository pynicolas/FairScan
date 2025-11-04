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
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.scale

class QuadDetectionService(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = FileUtil.loadMappedFile(context, "fairscan-quadrilateral.tflite")
        interpreter = Interpreter(model)
    }

    fun detectQuadrilateral(mask: Bitmap): Quad {
        val inputBuffer = bitmapToInputBuffer(mask)
        val outputBuffer = Array(1) { FloatArray(8) } // 4 points (x, y) normalized in [0, 1]

        interpreter.run(inputBuffer, outputBuffer)

        val vertices = outputBuffer[0]
            .toList()
            .chunked(2)
            .map { (x, y) -> Point(x.toDouble() * mask.width, y.toDouble() * mask.height) }
        return createQuad(vertices)
    }

    // TODO Review
    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        val inputWidth = 256
        val inputHeight = 256

        val resized = bitmap.scale(inputWidth, inputHeight)

        val buffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            val gray = (r + g + b) / 3f / 255f
            buffer.putFloat(gray)
        }

        buffer.rewind()
        return buffer
    }
}
