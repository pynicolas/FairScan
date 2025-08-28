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

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

class OpenCvTransformations : ImageTransformations {
    override fun rotate(inputFile: File, outputFile: File, clockwise: Boolean) {
        val src: Mat = Imgcodecs.imread(inputFile.absolutePath)

        require (!src.empty()) { "Could not load image from ${inputFile.absolutePath}" }

        val dst = Mat()
        Core.rotate(src, dst,
            if (clockwise) Core.ROTATE_90_CLOCKWISE else Core.ROTATE_90_COUNTERCLOCKWISE
        )

        if (!Imgcodecs.imwrite(outputFile.absolutePath, dst)) {
            throw RuntimeException("Could not write image to ${outputFile.absolutePath}")
        }

        src.release()
        dst.release()
    }
}
