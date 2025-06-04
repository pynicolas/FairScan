package org.mydomain.myscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class MainViewModel(
    private val imageSegmentationService: ImageSegmentationService,
    private val imageRepository: ImageRepository,
): ViewModel() {

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MainViewModel(ImageSegmentationService(context), ImageRepository(context.filesDir)) as T
            }
        }
    }

    private var _liveAnalysisState = MutableStateFlow(LiveAnalysisState())
    val liveAnalysisState: StateFlow<LiveAnalysisState> = _liveAnalysisState.asStateFlow()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Camera)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _pageIds = MutableStateFlow<List<String>>(imageRepository.imageIds())
    val pageIds: StateFlow<List<String>> = _pageIds

    private var _pageToValidate = MutableStateFlow<Bitmap?>(null)
    val pageToValidate: StateFlow<Bitmap?> = _pageToValidate.asStateFlow()

    init {
        viewModelScope.launch {
            imageSegmentationService.initialize()
            imageSegmentationService.segmentation
                .filterNotNull()
                .map {
                    val binaryMask = it.segmentation.toBinaryMask()
                    LiveAnalysisState(
                        inferenceTime = it.inferenceTime,
                        binaryMask = binaryMask,
                        documentQuad = detectDocumentQuad(binaryMask)
                    )
                }
                .collect {
                    _liveAnalysisState.value = it
                }
        }
    }

    fun segment(imageProxy: ImageProxy) {
        viewModelScope.launch {
            imageSegmentationService.runSegmentationAndEmit(
                imageProxy.toBitmap(),
                imageProxy.imageInfo.rotationDegrees,
            )
            imageProxy.close()
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun processCapturedImageThen(imageProxy: ImageProxy, onResult: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            _pageToValidate.value = processCapturedImage(imageProxy)
            onResult(_pageToValidate.value)
        }
    }

    private suspend fun processCapturedImage(imageProxy: ImageProxy): Bitmap? = withContext(Dispatchers.IO) {
        var corrected: Bitmap? = null
        val bitmap = imageProxy.toBitmap().rotate(imageProxy.imageInfo.rotationDegrees)
        val segmentation = imageSegmentationService.runSegmentationAndReturn(bitmap, 0)
        if (segmentation != null) {
            val mask = segmentation.segmentation.toBinaryMask()
            val quad = detectDocumentQuad(mask)
            if (quad != null) {
                val resizedQuad = quad.scaledTo(mask.width, mask.height, bitmap.width, bitmap.height)
                corrected = extractDocument(bitmap, resizedQuad)
            }
        }
        return@withContext corrected
    }

    fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    fun addPage(bitmap: Bitmap, quality: Int = 75) {
        val resized = resizeImage(bitmap)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val jpegBytes = outputStream.toByteArray()
        imageRepository.add(jpegBytes)
        _pageIds.value = imageRepository.imageIds()
    }

    fun deletePage(id: String) {
        imageRepository.delete(id)
        _pageIds.value = imageRepository.imageIds()
    }

    fun pageCount(): Int = pageIds.value.size

    fun getBitmap(id: String): Bitmap {
        val bytes = imageRepository.getContent(id)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun createPdf(outputStream: OutputStream) {
        val jpegs = imageRepository.imageIds().asSequence()
            .map { id -> imageRepository.getContent(id) }
        writePdfFromJpegs(jpegs, outputStream)
    }
}
