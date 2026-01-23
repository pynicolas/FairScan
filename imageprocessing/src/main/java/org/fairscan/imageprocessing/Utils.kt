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
package org.fairscan.imageprocessing

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

fun resizeForMaxPixels(img: Mat, maxPixels: Double): Mat {
    val origPixels = img.width() * img.height()
    if (origPixels <= maxPixels) {
        return img.clone()
    }
    val scale = sqrt(maxPixels / origPixels)
    val size = Size(img.width() * scale, img.height() * scale)
    val resizedImg = Mat()
    Imgproc.resize(img, resizedImg, size, 0.0, 0.0, Imgproc.INTER_AREA)
    return resizedImg
}
