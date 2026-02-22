/*
 * Copyright 2026 Philipp Hasper
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
package org.fairscan.app.ui.screens.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val LOUPE_BORDER_WIDTH = 3.dp
private const val ZOOM_FACTOR = 3f

/**
 * Layout parameters for the magnifying-glass loupe.
 *
 * Use `LoupeLayoutConfig<Dp>` in Compose UI code and [toPx] to convert to
 * `LoupeLayoutConfig<Float>` for pixel-level calculations.
 */
data class LoupeLayoutConfig<T>(
    /** Radius of the loupe circle */
    val loupeRadius: T,
    /** Gap between the finger and the nearest edge of the loupe */
    val verticalOffset: T,
    /** Minimum space between the loupe and the screen edge */
    val screenMargin: T,
) {
    companion object {
        val Default = LoupeLayoutConfig(
            loupeRadius = 60.dp,
            verticalOffset = 40.dp,
            screenMargin = 8.dp,
        )
    }
}

/** Convert a dp-valued config to pixels using the current [LocalDensity]. */
@Composable
internal fun LoupeLayoutConfig<Dp>.toPx(): LoupeLayoutConfig<Float> {
    val density = LocalDensity.current
    return with(density) {
        LoupeLayoutConfig(
            loupeRadius = loupeRadius.toPx(),
            verticalOffset = verticalOffset.toPx(),
            screenMargin = screenMargin.toPx(),
        )
    }
}

/**
 * Displays a magnifying glass / loupe showing a zoomed-in patch of [bitmap]
 * centred around [focusPosition].
 *
 * Positioning rules:
 *  1. By default, the loupe is placed **above** the finger.
 *  2. If there is not enough room above, it moves to the **left** of the finger.
 *  3. If there is not enough room on the left either, it moves to the **right**.
 *
 * @param bitmap          The full source bitmap (original image).
 * @param fingerPosition  Current finger position in screen (container) coordinates, used for loupe placement.
 * @param focusPosition   The exact point to zoom into (e.g. corner or edge midpoint) in screen coordinates.
 * @param containerSize   Size of the full-screen container.
 * @param displaySize     Size of the image as rendered (letterboxed inside the container).
 */
@Composable
fun MagnifyingGlass(
    bitmap: Bitmap,
    fingerPosition: Offset,
    focusPosition: Offset,
    containerSize: IntSize,
    displaySize: IntSize,
    configDp: LoupeLayoutConfig<Dp> = LoupeLayoutConfig.Default,
) {
    val density = LocalDensity.current
    val configPx = configDp.toPx()
    val borderWidth = with(density) { LOUPE_BORDER_WIDTH.toPx() }

    // compute loupe centre position
    val loupeCenter = computeLoupeCenter(
        dragPosition = fingerPosition,
        configPx = configPx,
        containerWidth = containerSize.width.toFloat(),
    )

    // compute the bitmap region to sample
    val imageOffset = QuadCoordinateUtils.getImageOffset(containerSize, displaySize)

    // Focus position mapped to bitmap pixel coordinates
    val bitmapX = ((focusPosition.x - imageOffset.width) / displaySize.width * bitmap.width)
        .coerceIn(0f, (bitmap.width - 1).toFloat())
    val bitmapY = ((focusPosition.y - imageOffset.height) / displaySize.height * bitmap.height)
        .coerceIn(0f, (bitmap.height - 1).toFloat())

    // How many bitmap pixels the loupe shows in each direction
    val bitmapRegionHalf = (bitmap.width / displaySize.width.toFloat()) * configPx.loupeRadius / ZOOM_FACTOR

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    val borderColor = MaterialTheme.colorScheme.primary
    val crosshairColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val backgroundColor = MaterialTheme.colorScheme.background

    Canvas(
        modifier = Modifier
            .size(configDp.loupeRadius * 2)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(
                        IntOffset(
                            (loupeCenter.x - configPx.loupeRadius).roundToInt(),
                            (loupeCenter.y - configPx.loupeRadius).roundToInt()
                        )
                    )
                }
            }
    ) {
        val circlePath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    0f, 0f, configPx.loupeRadius * 2, configPx.loupeRadius * 2
                )
            )
        }

        clipPath(circlePath) {
            // Fill background so areas outside the image are opaque
            drawRect(color = backgroundColor)

            // Source rect in bitmap coordinates
            val srcLeft = (bitmapX - bitmapRegionHalf).toInt().coerceAtLeast(0)
            val srcTop = (bitmapY - bitmapRegionHalf).toInt().coerceAtLeast(0)
            val srcRight = (bitmapX + bitmapRegionHalf).toInt().coerceAtMost(bitmap.width)
            val srcBottom = (bitmapY + bitmapRegionHalf).toInt().coerceAtMost(bitmap.height)

            if (srcRight > srcLeft && srcBottom > srcTop) {

                // Destination offset – compensate when the source rect was clamped
                val dstOffsetX = ((srcLeft - (bitmapX - bitmapRegionHalf)) / (2 * bitmapRegionHalf) * configPx.loupeRadius * 2)
                val dstOffsetY = ((srcTop - (bitmapY - bitmapRegionHalf)) / (2 * bitmapRegionHalf) * configPx.loupeRadius * 2)

                val dstWidth = ((srcRight - srcLeft) / (2 * bitmapRegionHalf) * configPx.loupeRadius * 2).toInt()
                val dstHeight = ((srcBottom - srcTop) / (2 * bitmapRegionHalf) * configPx.loupeRadius * 2).toInt()

                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(srcLeft, srcTop),
                    srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
                    dstOffset = IntOffset(dstOffsetX.toInt(), dstOffsetY.toInt()),
                    dstSize = IntSize(dstWidth, dstHeight),
                )
            }

            // Crosshair
            val center = Offset(configPx.loupeRadius, configPx.loupeRadius)
            val crosshairLen = configPx.loupeRadius * 0.25f
            drawLine(crosshairColor, Offset(center.x - crosshairLen, center.y), Offset(center.x + crosshairLen, center.y), strokeWidth = 2f)
            drawLine(crosshairColor, Offset(center.x, center.y - crosshairLen), Offset(center.x, center.y + crosshairLen), strokeWidth = 2f)
        }

        // Border
        drawCircle(
            color = borderColor,
            radius = configPx.loupeRadius - borderWidth / 2,
            center = Offset(configPx.loupeRadius, configPx.loupeRadius),
            style = Stroke(width = borderWidth)
        )
    }
}

/**
 * Decides where the loupe centre should be.
 *
 * Priority:
 *  1. Above the finger (centred horizontally, clamped to screen edges).
 *  2. If no vertical room -> to the left.
 *  3. If no room on the left -> to the right.
 */
internal fun computeLoupeCenter(
    dragPosition: Offset,
    configPx: LoupeLayoutConfig<Float>,
    containerWidth: Float,
): Offset {
    val loupeRadius = configPx.loupeRadius
    val verticalOffset = configPx.verticalOffset
    val screenMargin = configPx.screenMargin

    // Try above
    val aboveCenterY = dragPosition.y - verticalOffset - loupeRadius
    if (aboveCenterY - loupeRadius >= screenMargin) {
        // Enough room above -> place centred horizontally on the finger, clamped to screen edges
        val cx = dragPosition.x.coerceIn(screenMargin + loupeRadius, containerWidth - screenMargin - loupeRadius)
        return Offset(cx, aboveCenterY)
    }

    // Not enough room above -> try left
    val leftCenterX = dragPosition.x - verticalOffset - loupeRadius
    if (leftCenterX - loupeRadius >= screenMargin) {
        val cy = dragPosition.y.coerceIn(screenMargin + loupeRadius, Float.MAX_VALUE)
        return Offset(leftCenterX, cy)
    }

    // Not enough room on the left -> place right
    val rightCenterX = dragPosition.x + verticalOffset + loupeRadius
    val cx = rightCenterX.coerceAtMost(containerWidth - screenMargin - loupeRadius)
    val cy = dragPosition.y.coerceIn(screenMargin + loupeRadius, Float.MAX_VALUE)
    return Offset(cx, cy)
}
