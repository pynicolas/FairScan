package org.fairscan.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/**
 * Convert a (grayscale / binary) mask to an ARGB bitmap where alpha = luminance.
 * This keeps soft edges if the mask is soft; for a strict binary mask set useThreshold=true.
 */
fun maskToAlphaBitmap(mask: Bitmap, useThreshold: Boolean = false, threshold: Int = 128): Bitmap {
    val w = mask.width
    val h = mask.height
    val out = createBitmap(w, h)

    val pixels = IntArray(w * h)
    mask.getPixels(pixels, 0, w, 0, 0, w, h)

    for (i in pixels.indices) {
        val p = pixels[i]
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        // compute luminance (0..255)
        val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
        val a = if (useThreshold) if (lum >= threshold) 255 else 0 else lum
        // white color with computed alpha
        pixels[i] = (a shl 24) or 0x00FFFFFF
    }

    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

/**
 * Overlay a mask onto the original bitmap while preserving the original texture underneath.
 *
 * - original: base image
 * - mask: binary or grayscale mask (any size) — it will be scaled to original's size
 * - overlayColor: ARGB color (alpha part is multiplied by mask alpha)
 * - useFilter: whether to bilinear-filter when scaling mask (false keeps crisp edges)
 */
fun overlayMaskAsAlpha(
    original: Bitmap,
    mask: Bitmap,
    overlayColor: Int = Color.argb(128, 255, 0, 0),
    useThreshold: Boolean = true,
    threshold: Int = 128,
    useFilter: Boolean = false,
): Bitmap {
    val w = original.width
    val h = original.height

    // 1) scale mask to match original size
    val scaledMask = mask.scale(w, h, useFilter)

    // 2) convert scaled mask to ARGB where alpha = luminance (or binary if thresholded)
    val alphaMask = maskToAlphaBitmap(scaledMask, useThreshold, threshold)

    // 3) create a full-color bitmap (destination) filled with overlayColor
    val colored = createBitmap(w, h)
    val cCanvas = Canvas(colored)
    cCanvas.drawColor(overlayColor) // overlayColor contains desired base alpha

    // 4) keep color only where alphaMask is present: DST_IN multiplies dest by src alpha
    val maskPaint = Paint()
    maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    cCanvas.drawBitmap(alphaMask, 0f, 0f, maskPaint)
    maskPaint.xfermode = null

    // 5) draw the colored&masked bitmap over the original (SRC_OVER default) -> preserves texture
    val result = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    canvas.drawBitmap(colored, 0f, 0f, null)

    scaledMask.recycle()
    alphaMask.recycle()
    colored.recycle()

    return result
}

fun overlayQuadOnBitmap(
    original: Bitmap,
    quad: Quad,
    lineColor: Int = Color.RED,
    lineWidth: Float = 5f,
): Bitmap {
    val result = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    val paint = Paint().apply {
        color = lineColor
        strokeWidth = lineWidth
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    val path = Path().apply {
        moveTo(quad.topLeft.x.toFloat(), quad.topLeft.y.toFloat())
        lineTo(quad.topRight.x.toFloat(), quad.topRight.y.toFloat())
        lineTo(quad.bottomRight.x.toFloat(), quad.bottomRight.y.toFloat())
        lineTo(quad.bottomLeft.x.toFloat(), quad.bottomLeft.y.toFloat())
        close()
    }

    canvas.drawPath(path, paint)

    return result
}
