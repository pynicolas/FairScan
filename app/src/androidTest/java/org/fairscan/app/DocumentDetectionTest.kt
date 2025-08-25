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
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class DocumentDetectionTest {
    @Test
    fun extractDocumentFromImage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.fairscan.app", appContext.packageName)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val segmentationService = ImageSegmentationService(context)
        segmentationService.initialize()
        OpenCVLoader.initLocal()

        listOf("img01.jpg", "img02.jpg", "img03.jpg").forEach { imageFileName ->
            val inputStream = context.assets.open("uncropped/$imageFileName")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            var outputBitmap: Bitmap? = null

            val segmentationResult = runBlocking {
                segmentationService.runSegmentationAndReturn(bitmap, 0)
            }
            if (segmentationResult != null) {
                val mask = segmentationResult.segmentation.toBinaryMask()
                val quad = detectDocumentQuad(mask)
                if (quad != null) {
                    val resizedQuad =
                        quad.scaledTo(mask.width, mask.height, bitmap.width, bitmap.height)
                    outputBitmap = extractDocument(bitmap, resizedQuad, 0)
                    val file = File(context.getExternalFilesDir(null), imageFileName)
                    FileOutputStream(file).use {
                        outputBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    }
                    Log.i("DocumentDetectionTest", "Image saved to ${file.absolutePath}")
                }
            }
            if (outputBitmap == null) {
                fail("Failed to extract document from image $imageFileName")
            }
        }
    }
}