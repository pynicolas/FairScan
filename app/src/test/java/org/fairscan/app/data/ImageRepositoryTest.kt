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
package org.fairscan.app.data

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.domain.PageMetadata
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageRepositoryTest {

    @get:Rule
    var folder: TemporaryFolder = TemporaryFolder()

    private var _filesDir: File? = null

    val quad1 = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    val metadata1 = PageMetadata(quad1, 90, true)

    fun getFilesDir(): File {
        if (_filesDir == null) {
            _filesDir = folder.newFolder("files_dir")
        }
        return _filesDir!!
    }

    fun repo(): ImageRepository {
        val transformations = object : ImageTransformations {
            override fun rotate(inputFile: File, outputFile: File, clockwise: Boolean) {
                inputFile.copyTo(outputFile)
            }
            override fun resize(inputFile: File, outputFile: File, maxSize: Int) {
                outputFile.writeBytes(byteArrayOf(inputFile.readBytes()[0]))
            }
        }
        return ImageRepository(getFilesDir(), transformations, 200)
    }

    @Test
    fun add_image() {
        val repo = repo()
        assertThat(repo.imageIds()).isEmpty()
        val bytes = byteArrayOf(101, 102, 103)
        repo.add(bytes, byteArrayOf(51), metadata1)
        assertThat(repo.imageIds()).hasSize(1)
        val id = repo.imageIds()[0]
        assertThat(repo.getContent(id)).isEqualTo(bytes)
        assertThat(repo.getThumbnail(id)).isEqualTo(byteArrayOf(101))

        assertThat(repo().getPageMetadata("x")).isNull()
        val metadata = repo.getPageMetadata(id)
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.normalizedQuad).isEqualTo(quad1)
        assertThat(metadata.rotationDegrees).isEqualTo(metadata1.rotationDegrees)
        assertThat(metadata.isColored).isEqualTo(metadata1.isColored)
    }

    @Test
    fun delete_image() {
        val repo = repo()
        val bytes = byteArrayOf(101, 102, 103)
        repo.add(bytes, byteArrayOf(51), metadata1)
        assertThat(repo.imageIds()).hasSize(1)
        repo.delete(repo.imageIds()[0])
        assertThat(repo.imageIds()).isEmpty()
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun delete_unknown_id() {
        val repo = repo()
        repo.delete("x")
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun `should find existing files at initialization with no json`() {
        val scanDir = File(getFilesDir(), SCAN_DIR_NAME)
        scanDir.mkdirs()
        File(scanDir, "1.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("1.jpg")
    }

    @Test
    fun `should filter pages in json at initialization`() {
        val scanDir = File(getFilesDir(), SCAN_DIR_NAME)
        scanDir.mkdirs()
        val json = """{"pages":[{"file":"1.jpg"}, {"file":"2.jpg"}]}"""
        File(scanDir, "document.json").writeText(json)
        File(scanDir, "2.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("2.jpg")
    }

    @Test
    fun `should return null on invalid id`() {
        val repo = repo()
        assertThat(repo.imageIds()).isEmpty()
        assertThat(repo.getContent("x")).isNull()
    }

    @Test
    fun `clear should delete pages`() {
        val bytes = byteArrayOf(101, 102, 103)
        val repo1 = repo()
        repo1.add(bytes, byteArrayOf(51), metadata1)
        assertThat(repo1.imageIds()).isNotEmpty()
        repo1.clear()
        assertThat(repo1.imageIds()).isEmpty()
        assertThat(File(getFilesDir(), SCAN_DIR_NAME)
            .listFiles { f -> f.name.endsWith(".jpg") })
            .isEmpty()
        assertThat(File(getFilesDir(), THUMBNAIL_DIR_NAME)
            .listFiles { f -> f.name.endsWith(".jpg") })
            .isEmpty()
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun rotate() {
        val repo = repo()
        repo.add(byteArrayOf(101, 102, 103), byteArrayOf(51), metadata1)
        val id0 = repo.imageIds().last()
        val baseId = id0.substring(0, id0.length - 4)

        repo.rotate(id0, true)
        val id1 = repo.imageIds().last()
        assertThat(id1).isEqualTo("$baseId-90.jpg")

        repo.rotate(id1, true)
        val id2 = repo.imageIds().last()
        assertThat(id2).isEqualTo("$baseId-180.jpg")

        repo.rotate(id2, true)
        val id3 = repo.imageIds().last()
        assertThat(id3).isEqualTo("$baseId-270.jpg")

        repo.rotate(id3, true)
        val id4 = repo.imageIds().last()
        assertThat(id4).isEqualTo("$baseId.jpg")

        repo.rotate(id4, false)
        val id5 = repo.imageIds().last()
        assertThat(id5).isEqualTo("$baseId-270.jpg")
    }

    @Test
    fun movePage() {
        val repo = repo()
        repo.add(byteArrayOf(101), byteArrayOf(51), metadata1)
        Thread.sleep(1L) // to avoid file name clashes
        repo.add(byteArrayOf(110), byteArrayOf(51), metadata1)
        val id0 = repo.imageIds().first()
        val id1 = repo.imageIds().last()
        repo.movePage(id1, 0)
        assertThat(repo.imageIds()).containsExactly(id1, id0)

        val repo2 = repo()
        assertThat(repo2.imageIds()).containsExactly(id1, id0)
    }

    @Test
    fun metadata() {
        val quad = quad1.toSerializable()

        assertThat(Page("f1", null, 0, true).toMetadata()).isNull()
        assertThat(Page("f1", quad, 0, null).toMetadata()).isNull()

        listOf(true, false).forEach { isColored ->
            val metadata = Page("f1", quad, 0, isColored).toMetadata()
            assertThat(metadata).isNotNull()
            assertThat(metadata!!.isColored).isEqualTo(isColored)
        }
    }
}
