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

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.data.Logger
import org.fairscan.imageprocessing.CameraIntrinsics
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.BLACK_AND_WHITE
import org.fairscan.imageprocessing.ColorMode.COLOR
import org.fairscan.imageprocessing.ColorMode.GRAYSCALE
import org.fairscan.imageprocessing.EstimatedDimensions
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.OpticalMeasures
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportPreparationTest {

    @get:Rule
    var folder: TemporaryFolder = TemporaryFolder()

    private val testScope = TestScope()

    private val quad = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    private val metadata = PageMetadata(
        quad, Rotation.R0, COLOR,
        ImageSize(1600, 1200),
        OpticalMeasures(CameraIntrinsics(42.0f, 43.0f), 44.0f),
    )

    private suspend fun repoWithPage(colorMode: ColorMode): ImageRepository {
        val transformations = object : ImageTransformations {
            override fun rotate(input: Jpeg, rotationDegrees: Int): Jpeg = input
            override fun resizeToThumbnail(input: Jpeg): Jpeg = input
            override fun process(source: Jpeg, metadata: PageMetadata, colorMode: ColorMode): Jpeg =
                throw UnsupportedOperationException()
        }
        val repo = ImageRepository(
            folder.newFolder(), transformations, testScope, Logger { _, _, _ -> })
        repo.add(Jpeg(byteArrayOf(1, 2, 3)), Jpeg(byteArrayOf(4)), metadata, colorMode)
        return repo
    }

    @Test
    fun black_and_white_pages_carry_a_bitonal_provider() = runTest {
        for (quality in ExportQuality.entries) {
            val pages = pagesToExport(repoWithPage(BLACK_AND_WHITE), quality)
            assertThat(pages).hasSize(1)
            assertThat(pages.first().bitonal)
                .describedAs("bitonal provider for %s", quality)
                .isNotNull()
        }
    }

    @Test
    fun other_color_modes_do_not() = runTest {
        for (colorMode in listOf(COLOR, GRAYSCALE)) {
            for (quality in ExportQuality.entries) {
                val pages = pagesToExport(repoWithPage(colorMode), quality)
                assertThat(pages).hasSize(1)
                assertThat(pages.first().bitonal)
                    .describedAs("bitonal provider for %s at %s", colorMode, quality)
                    .isNull()
            }
        }
    }

    @Test
    fun black_and_white_targets_its_resolution_on_the_actual_page_size() {
        val a4 = EstimatedDimensions.Physical(210.0, 297.0)
        // 300 dpi on A4 is 2480 x 3508 pixels
        assertThat(ExportQuality.BALANCED.bitonalMaxPixels(a4))
            .isCloseTo(2480L * 3508, within(10_000L))
        // A receipt at the same setting needs far fewer pixels for the same sharpness
        val receipt = EstimatedDimensions.Physical(80.0, 200.0)
        assertThat(ExportQuality.BALANCED.bitonalMaxPixels(receipt))
            .isLessThan(ExportQuality.BALANCED.bitonalMaxPixels(a4))
    }

    @Test
    fun black_and_white_stays_between_the_normal_budget_and_the_memory_ceiling() {
        val sizes = listOf(
            EstimatedDimensions.Physical(210.0, 297.0),
            EstimatedDimensions.Physical(50.0, 50.0),
            EstimatedDimensions.Ratio(1.0, 1.41),
        )
        for (quality in ExportQuality.entries) {
            for (size in sizes) {
                assertThat(quality.bitonalMaxPixels(size))
                    .describedAs("%s at %s", quality, size)
                    .isBetween(quality.maxPixels, 20_000_000)
            }
        }
    }
}
