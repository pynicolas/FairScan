package org.fairscan.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fairscan.imageprocessing.ImageRect
import org.fairscan.imageprocessing.OcrTextBox
import java.io.File

class OcrService(private val context: Context) {

    private var tess: TessBaseAPI? = null
    private val mutex = Mutex()

    fun initialize() {
        prepareTessdata(context)

        val tess = TessBaseAPI()

        val dataPath: String = File(context.filesDir, "tesseract").absolutePath

        // Initialize API for specified language
        // (can be called multiple times during Tesseract lifetime)
        if (!tess.init(dataPath, "eng")) { // could be multiple languages, like "eng+deu+fra"
            tess.recycle()
            return
        }

        this.tess = tess
    }

    // FIXME: Tesseract language-specific data should be downloaded from the SettingsScreen
    fun prepareTessdata(context: Context) {
        val destDir = File(context.filesDir, "tesseract/tessdata")
        val destFile = File(destDir, "eng.traineddata")
        if (destFile.exists()) return

        destDir.mkdirs()
        context.assets.open("tesseract/tessdata_fast/eng.traineddata").use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
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
