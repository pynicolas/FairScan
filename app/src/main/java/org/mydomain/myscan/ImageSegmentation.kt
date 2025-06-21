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
package org.mydomain.myscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageSegmentationService(private val context: Context) {

    companion object {
        private const val TAG = "ImageSegmentation"
    }

    private val _segmentation = MutableStateFlow<SegmentationResult?>(null)
    val segmentation: StateFlow<SegmentationResult?> = _segmentation.asStateFlow()

    private var interpreter: Interpreter? = null
    private val inferenceLock = Mutex()

    fun initialize() {
        interpreter = try {
            val litertBuffer = FileUtil.loadMappedFile(context, "document-segmentation-model.tflite")
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

        val rotation = -rotationDegrees / 90
        val (_, h, w, _) = interpreter.getOutputTensor(0).shape()
        val imageProcessor =
            ImageProcessor
                .Builder()
                .add(ResizeOp(h, w, ResizeOp.ResizeMethod.BILINEAR))
                .add(Rot90Op(rotation))
                .add(NormalizeOp(127.5f, 127.5f)) // TODO check if it's correct
                .build()
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val segmentResult = segment(interpreter, processedImage)

        val inferenceTime = SystemClock.uptimeMillis() - startTime
        return SegmentationResult(segmentResult, inferenceTime)
    }

    suspend fun runSegmentationAndReturn(bitmap: Bitmap, rotationDegrees: Int): SegmentationResult? {
        if (interpreter == null) {
            return null
        }
        return inferenceLock.withLock {
            runSegmentation(interpreter!!, bitmap, rotationDegrees)
        }
    }

    suspend fun runSegmentationAndEmit(bitmap: Bitmap, rotationDegrees: Int) {
        try {
            withContext(Dispatchers.IO) {
                val segmentationResult = runSegmentationAndReturn(bitmap, rotationDegrees)
                if (isActive) {
                    _segmentation.value = segmentationResult
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred in image segmentation: ${e.message}")
        }
    }

    private fun segment(interpreter: Interpreter, tensorImage: TensorImage): Segmentation {
        val (_, h, w, _) = interpreter.getOutputTensor(0).shape()
        val outputBuffer = ByteBuffer.allocateDirect(4 * h * w)
        outputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.rewind()
        interpreter.run(tensorImage.tensorBuffer.buffer, outputBuffer)
        outputBuffer.rewind()
        val mask = generateMaskFromOutputBuffer(outputBuffer, w, h)
        return Segmentation(mask)
    }

    private fun generateMaskFromOutputBuffer(outputBuffer: ByteBuffer, width: Int, height: Int): Bitmap {
        outputBuffer.rewind()
        val floatArray = FloatArray(width * height)
        outputBuffer.asFloatBuffer()[floatArray]

        val pixels = IntArray(width * height)
        for (i in floatArray.indices) {
            val value = floatArray[i].coerceIn(0f, 1f)
            val gray = (value * 255).toInt()
            pixels[i] = Color.rgb(gray, gray, gray)
        }

        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    data class Segmentation(val mask: Bitmap) {
        fun toBinaryMask(): Bitmap = mask
    }

    data class SegmentationResult(
        val segmentation: Segmentation,
        val inferenceTime: Long
    )
}
