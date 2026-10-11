package org.fairscan.app.ui.screens.library

/** Document-space to viewport-space transform, shared by every page. */
internal data class PdfViewport(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun transform(
        factor: Float, focusX: Float, focusY: Float, panX: Float, panY: Float,
        viewportWidth: Float, viewportHeight: Float, documentHeight: Float,
    ): PdfViewport {
        val nextZoom = (zoom * factor).coerceIn(0.25f, 8f)
        val ratio = nextZoom / zoom
        return PdfViewport(nextZoom,
            focusX - (focusX - x) * ratio + panX,
            focusY - (focusY - y) * ratio + panY,
        ).bounded(viewportWidth, viewportHeight, documentHeight)
    }

    fun bounded(width: Float, height: Float, documentHeight: Float): PdfViewport {
        val scaledWidth = width * zoom
        val scaledHeight = documentHeight * zoom
        return copy(
            x = if (scaledWidth <= width) (width - scaledWidth) / 2f
                else x.coerceIn(width - scaledWidth, 0f),
            y = if (scaledHeight <= height) (height - scaledHeight) / 2f
                else y.coerceIn(height - scaledHeight, 0f),
        )
    }
}
