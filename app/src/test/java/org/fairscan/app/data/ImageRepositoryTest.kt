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
package org.fairscan.app.data

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation.R0
import org.fairscan.app.domain.Rotation.R180
import org.fairscan.app.domain.Rotation.R270
import org.fairscan.app.domain.Rotation.R90
import org.fairscan.imageprocessing.ColorMode
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

    private val testScope = TestScope()

    val quad1 = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    val metadata1 = PageMetadata(quad1, R90, ColorMode.COLOR)

    fun getFilesDir(): File {
        if (_filesDir == null) {
            _filesDir = folder.newFolder("files_dir")
        }
        return _filesDir!!
    }

    fun repo(): ImageRepository {
        val transformations = object : ImageTransformations {
            override fun rotate(input: Jpeg, rotationDegrees: Int, jpegQuality: Int): Jpeg {
                return input
            }
            override fun resize(input: Jpeg, maxSize: Int): Jpeg {
                return jpeg(input.bytes[0])
            }
        }
        return ImageRepository(getFilesDir(), transformations, 200, testScope)
    }

    @Test
    fun add_image() = runTest {
        val repo = repo()
        assertThat(repo.imageIds()).isEmpty()
        val jpeg = jpeg(101, 102, 103)
        repo.add(jpeg, jpeg(51), metadata1)
        assertThat(repo.imageIds()).hasSize(1)
        val id = repo.imageIds()[0]
        val key = PageViewKey(id, R0)
        assertThat(repo.jpegBytes(key)).isEqualTo(jpeg)
        assertThat(repo.getThumbnail(key)?.bytes).isEqualTo(byteArrayOf(101))

        val page = repo.pages().first()
        assertThat(page.id).isEqualTo(id)
        assertThat(page.manualRotation).isEqualTo(R0)
        val metadata = page.metadata
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.normalizedQuad).isEqualTo(quad1)
        assertThat(metadata.baseRotation).isEqualTo(metadata1.baseRotation)
        assertThat(metadata.autoColorMode).isEqualTo(metadata1.autoColorMode)
    }

    @Test
    fun delete_image() = runTest {
        val repo = repo()
        val jpeg = jpeg(101, 102, 103)
        repo.add(jpeg, jpeg(51), metadata1)
        assertThat(jpegFiles(processedDir())).hasSize(1)
        assertThat(jpegFiles(sourceDir())).hasSize(1)
        assertThat(repo.imageIds()).hasSize(1)
        repo.delete(repo.imageIds()[0])
        assertThat(repo.imageIds()).isEmpty()
        assertThat(jpegFiles(processedDir())).hasSize(0)
        assertThat(jpegFiles(sourceDir())).hasSize(0)
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun delete_unknown_id() = runTest {
        val repo = repo()
        repo.delete("x")
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun `should find existing files at initialization with no json`() = runTest {
        processedDir().mkdirs()
        File(processedDir(), "1.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("1")
    }

    @Test
    fun `should find existing files at initialization if json is invalid`() = runTest {
        writeDocumentDotJson("xxx")
        File(processedDir(), "1.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("1")
    }

    @Test
    fun `no json and two files with same id`() = runTest {
        processedDir().mkdirs()
        File(processedDir(), "1768153479486.jpg").writeBytes(byteArrayOf(101, 102, 103))
        File(processedDir(), "1768153479486-270.jpg").writeBytes(byteArrayOf(105, 106, 107))
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1768153479486")
    }

    @Test
    fun `should find existing files at initialization with no json and with rotation`() = runTest {
        processedDir().mkdirs()
        val bytes = byteArrayOf(101, 102, 103)
        File(processedDir(), "1-90.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1")
        assertThat(repo.jpegBytes(PageViewKey("1", R0))?.bytes).isEqualTo(bytes)
    }

    @Test
    fun `should filter pages in json at initialization`() = runTest {
        writeDocumentDotJson("""{"pages":[{"file":"1.jpg"}, {"file":"2.jpg"}]}""")
        File(processedDir(), "2.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("2")
    }

    @Test
    fun `should rename rotated files with no base file`() = runTest {
        processedDir().mkdirs()
        val bytes = byteArrayOf(105, 106, 107)
        File(processedDir(), "123-90.jpg").writeBytes(bytes)
        File(processedDir(), "123-270.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("123")
        val jpegFiles = jpegFiles(processedDir())
        assertThat(jpegFiles).hasSize(1).allMatch { it?.name == "123.jpg" }
    }

    @Test
    fun `should rename rotated files with no base file but listed in json`() = runTest {
        writeDocumentDotJson("""{"pages":[{"file":"1-90.jpg"}]}""")
        val bytes = byteArrayOf(105, 106, 107)
        File(processedDir(), "1-90.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1")
        assertThat(repo.jpegBytes(PageViewKey("1", R0))?.bytes).isEqualTo(bytes)
    }

    @Test
    fun `should return null on invalid id`() = runTest {
        val repo = repo()
        assertThat(repo.imageIds()).isEmpty()
        assertThat(repo.jpegBytes(PageViewKey("x", R0))).isNull()
    }

    @Test
    fun `clear should delete pages`() = runTest {
        val jpeg = jpeg(101, 102, 103)
        val repo1 = repo()
        repo1.add(jpeg, jpeg(51), metadata1)
        assertThat(repo1.imageIds()).isNotEmpty()
        repo1.clear()
        assertThat(repo1.imageIds()).isEmpty()
        assertThat(jpegFiles(processedDir())).isEmpty()
        assertThat(jpegFiles(sourceDir())).isEmpty()
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun rotate() = runTest {
        val repo = repo()
        repo.add(jpeg(101, 102, 103), jpeg(51), metadata1)
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

        val repo2 = repo()
        assertThat(repo2.imageIds()).containsExactly(id)
        assertThat(repo2.pages().last().manualRotation).isEqualTo(R270)
    }

    @Test
    fun rotate_unknown_id() = runTest {
        val repo = repo()
        repo.rotate("x", true)
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun movePage() = runTest {
        val repo = repo()
        repo.add(jpeg(101), jpeg(51), metadata1)
        Thread.sleep(1L) // to avoid file name clashes
        repo.add(jpeg(110), jpeg(51), metadata1)
        val id0 = repo.imageIds().first()
        val id1 = repo.imageIds().last()
        repo.movePage(id1, 0)
        assertThat(repo.imageIds()).containsExactly(id1, id0)

        val repo2 = repo()
        assertThat(repo2.imageIds()).containsExactly(id1, id0)
    }

    @Test
    fun move_unknown_id() = runTest {
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
            assertThat(metadata!!.autoColorMode).isEqualTo(
                if (isColored) ColorMode.COLOR else ColorMode.GRAYSCALE
            )
        }
    }

    @Test
    fun `pages with invalid metadata should be skipped`() = runTest {
        val bytes = byteArrayOf(105, 106, 107)

        writeDocumentDotJson("""{"version":2, "pages":[{"id":"1", "manualRotationDegrees":90}]}""")
        File(processedDir(), "1.jpg").writeBytes(byteArrayOf(101))
        File(processedDir(), "1-90.jpg").writeBytes(bytes)
        assertThat(repo().imageIds()).containsExactly("1")

        writeDocumentDotJson("""{"version":2, "pages":[{"id":"1", "manualRotationDegrees":42}]}""")
        File(processedDir(), "1.jpg").writeBytes(byteArrayOf(101))
        File(processedDir(), "1-42.jpg").writeBytes(bytes)
        assertThat(repo().imageIds()).isEmpty()
    }

    @Test
    fun last_added_source_file() = runTest {
        val repo = repo()
        assertThat(repo.lastAddedSourceFile()).isNull()
        repo.add(jpeg(101), jpeg(51), metadata1)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(51))
        Thread.sleep(1)
        repo.add(jpeg(102), jpeg(52), metadata1)
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

    private fun processedDir(): File = File(getFilesDir(), PROCESSED_DIR_NAME)
    private fun sourceDir(): File = File(getFilesDir(), SOURCE_DIR_NAME)

    private fun jpegFiles(dir: File): Array<out File?>?
        = dir.listFiles { f -> f.name.endsWith(".jpg") }

    private fun writeDocumentDotJson(json: String) {
        processedDir().mkdirs()
        File(processedDir(), "document.json").writeText(json)
    }

    suspend fun ImageRepository.imageIds(): PersistentList<String> =
        pages().map { it.id }.toPersistentList()

    private fun jpeg(vararg bytes: Byte) = Jpeg(bytes)
}
