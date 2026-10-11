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
package org.fairscan.app.ui.screens.library

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fairscan.app.R
import org.fairscan.app.platform.loadBitmapFromUri
import java.io.Closeable
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryDocumentViewer(
    uri: Uri,
    name: String,
    mimeType: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (mimeType == "application/pdf") {
            PdfDocumentContent(uri, name, Modifier.padding(padding))
        } else {
            ImageDocumentContent(uri, name, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ImageDocumentContent(uri: Uri, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val result by produceState<Result<Bitmap>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadBitmapFromUri(context.contentResolver, uri) }
                .onFailure { Log.e("LibraryViewer", "Could not decode image $uri", it) }
        }
    }
    when {
        result == null -> LoadingDocument(modifier)
        result!!.isFailure -> DocumentError(modifier)
        else -> Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Image(
                    bitmap = result!!.getOrThrow().asImageBitmap(),
                    contentDescription = name,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
    }
}


@Composable
private fun PdfDocumentContent(uri: Uri, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pdfState by produceState<Result<PdfPageSource>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { PdfPageSource(context, uri) }
                .onFailure { Log.e("LibraryViewer", "Could not open PDF $uri", it) }
        }
    }
    val source = pdfState?.getOrNull()
    DisposableEffect(source) { onDispose { source?.close() } }
    when {
        pdfState == null -> LoadingDocument(modifier)
        pdfState!!.isFailure -> DocumentError(modifier, pdfState!!.exceptionOrNull())
        else -> BoxWithConstraints(modifier.fillMaxSize().clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            val gap = with(density) { 8.dp.toPx() }
            if (width > gap * 2 && height > 0) {
                key(source, width, height) {
                    PdfReadingSurface(checkNotNull(source), name, width, height, gap)
                }
            }
        }
    }
}

@Composable
private fun PdfReadingSurface(pdf: PdfPageSource, name: String, width: Float, height: Float, gap: Float) {
    val pageWidth = width - gap * 2
    val heights = remember(pdf, width) { pdf.pageRatios.map { pageWidth * it } }
    val tops = remember(heights) {
        var top = gap
        heights.map { pageHeight -> top.also { top += pageHeight + gap } }
    }
    val documentHeight = tops.last() + heights.last() + gap
    var viewport by remember { mutableStateOf(PdfViewport().bounded(width, height, documentHeight)) }
    // Keep the last bitmap while fingers move; refine it once the zoom settles.
    val renderZoom by produceState(1.5f, viewport.zoom) {
        kotlinx.coroutines.delay(180)
        value = maxOf(0.5f, viewport.zoom * 1.5f)
    }
    var showPageDialog by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    val currentPage = tops.indices.maxByOrNull { index ->
        val top = tops[index] * viewport.zoom + viewport.y
        val bottom = top + heights[index] * viewport.zoom
        (minOf(bottom, height) - maxOf(top, 0f)).coerceAtLeast(0f)
    } ?: 0
    fun changeZoom(target: Float, focus: Offset = Offset(width / 2, height / 2)) {
        viewport = viewport.transform(target / viewport.zoom, focus.x, focus.y, 0f, 0f,
            width, height, documentHeight)
    }
    fun goToPage(index: Int, fit: Boolean = false) {
        val zoom = if (fit) minOf(1f, (height - gap * 12).coerceAtLeast(1f) / heights[index])
            .coerceAtLeast(0.25f) else viewport.zoom
        viewport = PdfViewport(zoom, (width - width * zoom) / 2,
            gap - tops[index] * zoom).bounded(width, height, documentHeight)
    }
    Box(Modifier.fillMaxSize()) {
        // Gestures are measured in the fixed viewport, never inside a scaled page.
        Box(Modifier.fillMaxSize()
            .pointerInput(pdf) {
                detectTapGestures(onDoubleTap = { focus ->
                    changeZoom(if (viewport.zoom >= 2f) 1f else 2.5f, focus)
                })
            }
            .pointerInput(pdf) {
                detectTransformGestures { centroid, pan, factor, _ ->
                    viewport = viewport.transform(factor, centroid.x, centroid.y, pan.x, pan.y,
                        width, height, documentHeight)
                }
            }) {
            for (index in tops.indices) {
                val pageTop = tops[index] * viewport.zoom + viewport.y
                val pageHeight = heights[index] * viewport.zoom
                if (pageTop > height + height / 2 || pageTop + pageHeight < -height / 2) continue
                key(index) {
                    PdfRenderedPage(pdf, index, (pageWidth * renderZoom).roundToInt(),
                        Rect(gap * viewport.zoom + viewport.x, pageTop,
                            (gap + pageWidth) * viewport.zoom + viewport.x, pageTop + pageHeight),
                        name)
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { changeZoom(viewport.zoom / 1.25f) }) {
                    Icon(Icons.Default.Remove, stringResource(R.string.library_zoom_out))
                }
                TextButton(onClick = { changeZoom(1f) }) {
                    Text("${(viewport.zoom * 100).roundToInt()}%")
                }
                IconButton(onClick = { changeZoom(viewport.zoom * 1.25f) }) {
                    Icon(Icons.Default.Add, stringResource(R.string.library_zoom_in))
                }
                IconButton(onClick = { goToPage(currentPage, fit = true) }) {
                    Icon(Icons.Default.FitScreen, stringResource(R.string.library_fit_page))
                }
                TextButton(onClick = {
                    pageInput = (currentPage + 1).toString()
                    showPageDialog = true
                }) {
                    Text(stringResource(R.string.library_page_count, currentPage + 1, pdf.pageCount))
                }
            }
        }
    }
    if (showPageDialog) {
        val chosenPage = pageInput.toIntOrNull()
        AlertDialog(
            onDismissRequest = { showPageDialog = false },
            title = { Text(stringResource(R.string.library_go_to_page)) },
            text = {
                OutlinedTextField(value = pageInput, onValueChange = { pageInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.library_page_count, currentPage + 1, pdf.pageCount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = {
                TextButton(enabled = chosenPage != null && chosenPage in 1..pdf.pageCount,
                    onClick = { goToPage(checkNotNull(chosenPage) - 1); showPageDialog = false }) {
                    Text(stringResource(R.string.open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PdfRenderedPage(source: PdfPageSource, index: Int, renderWidth: Int, bounds: Rect, name: String) {
    val result by produceState<Result<Bitmap>?>(null, source, index, renderWidth) {
        value = withContext(Dispatchers.IO) {
            runCatching { source.renderPage(index, renderWidth) }
                .onFailure { Log.e("LibraryViewer", "Could not render PDF page ${index + 1}", it) }
        }
    }
    val bitmap = result?.getOrNull()
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()
        .semantics { contentDescription = "$name, ${index + 1}" }) {
        drawRect(androidx.compose.ui.graphics.Color.White, bounds.topLeft, bounds.size)
        if (image != null) {
            drawImage(image,
                dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
                dstSize = IntSize(bounds.width.roundToInt().coerceAtLeast(1),
                    bounds.height.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.High)
        }
    }
    if (result == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (result!!.isFailure) {
        DocumentError(Modifier.fillMaxSize(), result!!.exceptionOrNull())
    }
}

@Composable
private fun LoadingDocument(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DocumentError(modifier: Modifier = Modifier, error: Throwable? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.library_view_error), color = MaterialTheme.colorScheme.error)
        error?.rootCause()?.let { cause ->
            val detail = buildString {
                append(cause.javaClass.simpleName)
                cause.message?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
            }.take(180)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Throwable.rootCause(): Throwable {
    var cause = this
    while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
    return cause
}

private class PdfPageSource(context: Context, uri: Uri) : Closeable {
    private val cachedFile = File.createTempFile("fairscan-view-", ".pdf", context.cacheDir)
    val pageCount: Int
    val pageRatios: List<Float>
    private var closed = false

    init {
        var openedRenderer: PdfRenderer? = null
        var openedDescriptor: ParcelFileDescriptor? = null
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF")
            input.use { source -> cachedFile.outputStream().use { source.copyTo(it) } }
            val fileDescriptor = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            openedDescriptor = fileDescriptor
            val pdfRenderer = PdfRenderer(fileDescriptor)
            openedRenderer = pdfRenderer
            pageCount = pdfRenderer.pageCount
            require(pageCount > 0) { "PDF has no pages" }
            pageRatios = (0 until pageCount).map { index ->
                pdfRenderer.openPage(index).use { it.height.toFloat() / it.width }
            }
            pdfRenderer.close()
            fileDescriptor.close()
        } catch (error: Throwable) {
            runCatching { openedRenderer?.close() }
            runCatching { openedDescriptor?.close() }
            cachedFile.delete()
            throw error
        }
    }

    @Synchronized
    fun renderPage(index: Int, requestedWidth: Int): Bitmap {
        check(!closed) { "PDF viewer is closed" }
        ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(index).use { page ->
                    val requestedScale = requestedWidth.toFloat() / page.width
                    val memoryScale = sqrt(MAX_PAGE_PIXELS.toFloat() / (page.width.toFloat() * page.height))
                    val scale = min(requestedScale, memoryScale)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        cachedFile.delete()
    }

    private companion object {
        const val MAX_PAGE_PIXELS = 6_000_000
    }
}
