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

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation.R0
import org.fairscan.app.domain.Rotation.R180
import org.fairscan.app.domain.Rotation.R270
import org.fairscan.app.domain.Rotation.R90
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
    val metadata1 = PageMetadata(quad1, R90, true)

    fun getFilesDir(): File {
        if (_filesDir == null) {
            _filesDir = folder.newFolder("files_dir")
        }
        return _filesDir!!
    }

    fun repo(): ImageRepository {
        val transformations = object : ImageTransformations {
            override fun rotate(inputFile: File, outputFile: File, rotationDegrees: Int, jpegQuality: Int) {
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
        val key = PageViewKey(id, R0)
        assertThat(repo.jpegBytes(key)).isEqualTo(bytes)
        assertThat(repo.getThumbnail(key)).isEqualTo(byteArrayOf(101))

        val page = repo.pages().first()
        assertThat(page.id).isEqualTo(id)
        assertThat(page.manualRotation).isEqualTo(R0)
        val metadata = page.metadata
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.normalizedQuad).isEqualTo(quad1)
        assertThat(metadata.baseRotation).isEqualTo(metadata1.baseRotation)
        assertThat(metadata.isColored).isEqualTo(metadata1.isColored)
    }

    @Test
    fun delete_image() {
        val repo = repo()
        val bytes = byteArrayOf(101, 102, 103)
        repo.add(bytes, byteArrayOf(51), metadata1)
        assertThat(jpegFiles(scanDir())).hasSize(1)
        assertThat(jpegFiles(sourceDir())).hasSize(1)
        assertThat(repo.imageIds()).hasSize(1)
        repo.delete(repo.imageIds()[0])
        assertThat(repo.imageIds()).isEmpty()
        assertThat(jpegFiles(scanDir())).hasSize(0)
        assertThat(jpegFiles(sourceDir())).hasSize(0)
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
        scanDir().mkdirs()
        File(scanDir(), "1.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("1")
    }

    @Test
    fun `should find existing files at initialization if json is invalid`() {
        writeDocumentDotJson("xxx")
        File(scanDir(), "1.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("1")
    }

    @Test
    fun `no json and two files with same id`() {
        scanDir().mkdirs()
        File(scanDir(), "1768153479486.jpg").writeBytes(byteArrayOf(101, 102, 103))
        File(scanDir(), "1768153479486-270.jpg").writeBytes(byteArrayOf(105, 106, 107))
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1768153479486")
    }

    @Test
    fun `should find existing files at initialization with no json and with rotation`() {
        scanDir().mkdirs()
        val bytes = byteArrayOf(101, 102, 103)
        File(scanDir(), "1-90.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1")
        assertThat(repo.jpegBytes("1")).isEqualTo(bytes)
    }

    @Test
    fun `should filter pages in json at initialization`() {
        writeDocumentDotJson("""{"pages":[{"file":"1.jpg"}, {"file":"2.jpg"}]}""")
        File(scanDir(), "2.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("2")
    }

    @Test
    fun `should rename rotated files with no base file`() {
        scanDir().mkdirs()
        val bytes = byteArrayOf(105, 106, 107)
        File(scanDir(), "123-90.jpg").writeBytes(bytes)
        File(scanDir(), "123-270.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("123")
        val jpegFiles = jpegFiles(scanDir())
        assertThat(jpegFiles).hasSize(1).allMatch { it?.name == "123.jpg" }
    }

    @Test
    fun `should rename rotated files with no base file but listed in json`() {
        writeDocumentDotJson("""{"pages":[{"file":"1-90.jpg"}]}""")
        val bytes = byteArrayOf(105, 106, 107)
        File(scanDir(), "1-90.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1")
        assertThat(repo.jpegBytes("1")).isEqualTo(bytes)
    }

    @Test
    fun `should return null on invalid id`() {
        val repo = repo()
        assertThat(repo.imageIds()).isEmpty()
        assertThat(repo.jpegBytes("x")).isNull()
    }

    @Test
    fun `clear should delete pages`() {
        val bytes = byteArrayOf(101, 102, 103)
        val repo1 = repo()
        repo1.add(bytes, byteArrayOf(51), metadata1)
        assertThat(repo1.imageIds()).isNotEmpty()
        repo1.clear()
        assertThat(repo1.imageIds()).isEmpty()
        assertThat(jpegFiles(scanDir())).isEmpty()
        assertThat(jpegFiles(sourceDir())).isEmpty()
        assertThat(jpegFiles(File(getFilesDir(), THUMBNAIL_DIR_NAME))).isEmpty()
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun rotate() {
        val repo = repo()
        repo.add(byteArrayOf(101, 102, 103), byteArrayOf(51), metadata1)
        assertThat(repo.pages().last().metadata).isEqualTo(metadata1)
        val id = repo.pages().last().id
        repo.rotate(id, true)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R90)
        repo.rotate(id, true)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R180)
        repo.rotate(id, true)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R270)
        repo.rotate(id, true)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R0)
        repo.rotate(id, false)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R270)
    }

    @Test
    fun rotate_unknown_id() {
        val repo = repo()
        repo.rotate("x", true)
        assertThat(repo.imageIds()).isEmpty()
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
    fun move_unknown_id() {
        val repo = repo()
        repo.movePage("x", 0)
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun metadata() {
        val quad = quad1.toSerializable()

        assertThat(PageV2("1", 0, 0, null,true).toMetadata()).isNull()
        assertThat(PageV2("1", 0, 0, quad, null).toMetadata()).isNull()

        listOf(true, false).forEach { isColored ->
            val metadata = PageV2("1", 0, 0, quad, isColored).toMetadata()
            assertThat(metadata).isNotNull()
            assertThat(metadata!!.isColored).isEqualTo(isColored)
        }
    }

    @Test
    fun `pages with invalid metadata should be skipped`() {
        val bytes = byteArrayOf(105, 106, 107)

        writeDocumentDotJson("""{"version":2, "pages":[{"id":"1", "manualRotationDegrees":90}]}""")
        File(scanDir(), "1.jpg").writeBytes(byteArrayOf(101))
        File(scanDir(), "1-90.jpg").writeBytes(bytes)
        assertThat(repo().imageIds()).containsExactly("1")

        writeDocumentDotJson("""{"version":2, "pages":[{"id":"1", "manualRotationDegrees":42}]}""")
        File(scanDir(), "1.jpg").writeBytes(byteArrayOf(101))
        File(scanDir(), "1-42.jpg").writeBytes(bytes)
        assertThat(repo().imageIds()).isEmpty()
    }

    @Test
    fun last_added_source_file() {
        val repo = repo()
        assertThat(repo.lastAddedSourceFile()).isNull()
        repo.add(byteArrayOf(101), byteArrayOf(51), metadata1)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(51))
        Thread.sleep(1)
        repo.add(byteArrayOf(102), byteArrayOf(52), metadata1)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(52))

        val id = repo.imageIds().last()
        repo.movePage(id, 0)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(52))
        repo.delete(id)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(51))

        val repo2 = repo()
        assertThat(repo2.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(51))

        repo2.clear()
        assertThat(repo2.lastAddedSourceFile()).isNull()
    }

    private fun scanDir(): File = File(getFilesDir(), SCAN_DIR_NAME)
    private fun sourceDir(): File = File(getFilesDir(), SOURCE_DIR_NAME)

    private fun jpegFiles(dir: File): Array<out File?>?
        = dir.listFiles { f -> f.name.endsWith(".jpg") }

    private fun writeDocumentDotJson(json: String) {
        scanDir().mkdirs()
        File(scanDir(), "document.json").writeText(json)
    }

    fun ImageRepository.imageIds(): PersistentList<String> =
        pages().map { it.id }.toPersistentList()
}
