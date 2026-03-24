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
package org.fairscan.app.domain

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.fairscan.app.ui.screens.camera.extractDocumentFromBitmap
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.scaledTo
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentDetectionTest {
    @Test
    fun extractDocumentFromImage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.fairscan.app", appContext.packageName)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val segmentationService = ImageSegmentationService(context) { _, _, _ -> }
        segmentationService.initialize()
        OpenCVLoader.initLocal()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        listOf("img01.jpg", "img02.jpg", "img03.jpg").forEach { imageFileName ->
            val inputStream = context.assets.open("uncropped/$imageFileName")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            var outputJpeg: ByteArray? = null

            val segmentationResult = runBlocking {
                segmentationService.runSegmentationAndReturn(bitmap)
            }
            if (segmentationResult != null) {
                val mask = segmentationResult.segmentation
                val quad = detectDocumentQuad(mask, ImageSize(bitmap.width, bitmap.height),false)
                if (quad != null) {
                    val resizedQuad =
                        quad.scaledTo(mask.width, mask.height, bitmap.width, bitmap.height)
                    outputJpeg = extractDocumentFromBitmap(bitmap, resizedQuad, 0, mask, scope).pageJpeg
                    val file = File(context.getExternalFilesDir(null), imageFileName)
                    file.writeBytes(outputJpeg)
                    Log.i("DocumentDetectionTest", "Image saved to ${file.absolutePath}")
                }
            }
            if (outputJpeg == null) {
                fail("Failed to extract document from image $imageFileName")
            }
        }
    }
}