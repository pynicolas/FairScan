/*
 * /*
 *  * Copyright 2025 Pierre-Yves Nicolas
 *  *
 *  * This program is free software: you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation, either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  * You should have received a copy of the GNU General Public License along with
 *  * this program. If not, see <https://www.gnu.org/licenses/>.
 *  */
 */
package org.mydomain.myscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ColorSpaceType
import org.tensorflow.lite.support.image.ImageProperties
import org.tensorflow.lite.support.image.TensorImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class ImageSegmentationService(private val context: Context) {

    companion object {
        private const val TAG = "ImageSegmentation"
    }

    private val _segmentation = MutableStateFlow<SegmentationResult?>(null)
    val segmentation: StateFlow<SegmentationResult?> = _segmentation.asStateFlow()

    private var interpreter: Interpreter? = null

    fun initialize() {
        interpreter = try {
            val litertBuffer = FileUtil.loadMappedFile(context, "mydeeplabv3.tflite")
            Log.i(TAG, "Loaded LiteRT model")
            val options = Interpreter.Options().apply {
                numThreads = 2
            }
            Interpreter(litertBuffer, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LiteRT model: ${e.message}")
            null
        }
    }

    private fun runSegmentation(interpreter: Interpreter, bitmap: Bitmap, rotationDegrees: Int): SegmentationResult {
        val startTime = SystemClock.uptimeMillis()

        val (_, _, h, w) = interpreter.getInputTensor(0).shape()
        // Preprocess manually into CHW float buffer
        val inputBuffer = bitmapToCHWFloatBuffer(bitmap, width = w, height = h, rotationDegrees)

        val (_, cOut, hOut, wOut) = interpreter.getOutputTensor(0).shape()
        val outputBuffer = FloatBuffer.allocate(cOut * hOut * wOut)

        // Run inference
        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)

        val inferenceTime = SystemClock.uptimeMillis() - startTime
        val segmentResult = processOutputBuffer(outputBuffer, wOut, hOut, cOut)
        return SegmentationResult(segmentResult, inferenceTime)
    }

    fun runSegmentationAndReturn(bitmap: Bitmap, rotationDegrees: Int): SegmentationResult? {
        if (interpreter != null) {
            return runSegmentation(interpreter!!, bitmap, rotationDegrees)
        }
        return null
    }

    suspend fun runSegmentationAndEmit(bitmap: Bitmap, rotationDegrees: Int) {
        if (interpreter == null) return
        try {
            withContext(Dispatchers.IO) {
                val segmentationResult = runSegmentation(interpreter!!, bitmap, rotationDegrees)
                if (isActive) {
                    _segmentation.value = segmentationResult
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred in image segmentation: ${e.message}")
        }
    }

    fun bitmapToCHWFloatBuffer(bitmap: Bitmap, width: Int, height: Int, rotationDegrees: Int): FloatBuffer {
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val resized = rotatedBitmap.scale(width, height)
        val buffer = FloatBuffer.allocate(1 * 3 * height * width)
        buffer.rewind()

        val mean = floatArrayOf(0.4611f, 0.4359f, 0.3905f)
        val std = floatArrayOf(0.2193f, 0.2150f, 0.2109f)

        val pixels = IntArray(width * height)
        resized.getPixels(pixels, 0, width, 0, 0, width, height)

        // Fill buffer in CHW order
        for (c in 0..2) {
            for (i in 0 until height) {
                for (j in 0 until width) {
                    val pixel = pixels[i * width + j]
                    val value = when (c) {
                        0 -> (pixel shr 16 and 0xFF) // R
                        1 -> (pixel shr 8 and 0xFF)  // G
                        2 -> (pixel and 0xFF)        // B
                        else -> 0
                    }
                    val normalized = (value / 255f - mean[c]) / std[c]
                    buffer.put(normalized)
                }
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun processOutputBuffer(outputBuffer: FloatBuffer, w: Int, h: Int, c: Int): Segmentation {
        outputBuffer.rewind()
        val inferenceData =
            InferenceData(width = w, height = h, channels = c, buffer = outputBuffer)
        val mask = generateMaskFromOutputBuffer(inferenceData)

        val imageProperties =
            ImageProperties
                .builder()
                .setWidth(inferenceData.width)
                .setHeight(inferenceData.height)
                .setColorSpaceType(ColorSpaceType.GRAYSCALE)
                .build()
        val maskImage = TensorImage()
        maskImage.load(mask, imageProperties)
        return Segmentation(maskImage)
    }

    private fun generateMaskFromOutputBuffer(inferenceData: InferenceData): ByteBuffer {
        val width = inferenceData.width
        val height = inferenceData.height
        val mask = ByteBuffer.allocateDirect(width * height)
        for (i in 0 until height) {
            for (j in 0 until width) {
                var maxIndex = 0
                var maxValue = inferenceData.buffer[i * width + j]

                for (c in 1 until inferenceData.channels) {
                    val value = inferenceData.buffer[c * height * width + i * width + j]
                    if (value > maxValue) {
                        maxValue = value
                        maxIndex = c
                    }
                }

                mask.put(i * width + j, maxIndex.toByte())
            }
        }
        return mask
    }

    data class Segmentation(val mask: TensorImage) {
        fun toBinaryMask(): Bitmap {
            val width = mask.width
            val height = mask.height
            val pixels = IntArray(width * height)
            for (i in 0 until height) {
                for (j in 0 until width) {
                    val index = i * width + j
                    val classId = mask.buffer[index].toInt() and 0xFF // Unsigned byte
                    pixels[index] = if (classId == 0) Color.BLACK else Color.WHITE
                }
            }
            return createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }

    data class SegmentationResult(
        val segmentation: Segmentation,
        val inferenceTime: Long
    )

    data class InferenceData(
        val width: Int,
        val height: Int,
        val channels: Int,
        val buffer: FloatBuffer,
    )
}
