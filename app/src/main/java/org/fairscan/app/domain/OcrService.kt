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
package org.fairscan.app.domain

import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fairscan.app.data.OcrLanguageRepository
import org.fairscan.imageprocessing.ImageRect
import org.fairscan.imageprocessing.OcrTextBox

class OcrService(
    private val ocrLanguageRepository: OcrLanguageRepository,
    private val scope: CoroutineScope,
) {
    private var tess: TessBaseAPI? = null

    private val mutex = Mutex()

    private var languageString = ""
    fun languageString() = languageString

    fun initialize() {
        scope.launch {
            ocrLanguageRepository.enabledLanguages.collect { _ -> reinitialize() }
        }
    }

    private suspend fun reinitialize() {
        mutex.withLock {
            tess?.recycle()
            tess = null

            languageString = ocrLanguageRepository.buildTesseractLanguageString()
            if (languageString.isEmpty()) return

            val dataPath = ocrLanguageRepository.tessdataDir.parent!!
            val newTess = TessBaseAPI()
            if (!newTess.init(dataPath, languageString)) {
                newTess.recycle()
                return
            }
            tess = newTess
        }
    }

    suspend fun runOcr(bitmap: Bitmap): List<OcrTextBox> {
        mutex.withLock {
            val tess = this.tess ?: return listOf()
            val textBoxes = mutableListOf<OcrTextBox>()
            tess.setImage(bitmap)
            tess.getUTF8Text() // Trigger text recognition
            val iterator = tess.resultIterator
            iterator.begin()
            do {
                val word = iterator.getUTF8Text(PageIteratorLevel.RIL_WORD) ?: continue
                val boundingBox = iterator.getBoundingRect(PageIteratorLevel.RIL_WORD)
                val confidence = iterator.confidence(PageIteratorLevel.RIL_WORD)
                if (confidence > 50) {
                    textBoxes.add(OcrTextBox(word, boundingBox.toImageRect()))
                }
            } while (iterator.next(PageIteratorLevel.RIL_WORD))
            iterator.delete()
            return textBoxes
        }
    }

    private fun Rect.toImageRect(): ImageRect = ImageRect(left, top, right, bottom)
}
