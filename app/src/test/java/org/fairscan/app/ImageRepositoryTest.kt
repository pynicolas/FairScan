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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageRepositoryTest {

    @get:Rule
    var folder: TemporaryFolder = TemporaryFolder()

    private var _filesDir: File? = null

    fun getFilesDir(): File {
        if (_filesDir == null) {
            _filesDir = folder.newFolder("files_dir")
        }
        return _filesDir!!
    }

    fun repo(): ImageRepository {
        return ImageRepository(getFilesDir(), {f1,f2,_->f1.copyTo(f2)})
    }

    @Test
    fun add_image() {
        val repo = repo()
        assertThat(repo.imageIds()).isEmpty()
        val bytes = byteArrayOf(101, 102, 103)
        repo.add(bytes)
        assertThat(repo.imageIds()).hasSize(1)
        assertThat(repo.getContent(repo.imageIds()[0])).isEqualTo(bytes)
    }

    @Test
    fun delete_image() {
        val repo = repo()
        val bytes = byteArrayOf(101, 102, 103)
        repo.add(bytes)
        assertThat(repo.imageIds()).hasSize(1)
        repo.delete(repo.imageIds()[0])
        assertThat(repo.imageIds()).isEmpty()
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun `should find existing files at initialization`() {
        val bytes = byteArrayOf(101, 102, 103)
        val repo1 = repo()
        assertThat(repo1.imageIds()).isEmpty()
        repo1.add(bytes)
        val repo2 = repo()
        assertThat(repo2.imageIds()).hasSize(1)
        assertThat(repo2.getContent(repo2.imageIds()[0])).isEqualTo(bytes)
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
        repo1.add(bytes)
        assertThat(repo1.imageIds()).isNotEmpty()
        repo1.clear()
        assertThat(repo1.imageIds()).isEmpty()
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun rotate() {
        val repo = repo()
        repo.add(byteArrayOf(101, 102, 103))
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
        repo.add(byteArrayOf(101))
        repo.add(byteArrayOf(110))
        val id0 = repo.imageIds().first()
        val id1 = repo.imageIds().last()
        repo.movePage(id1, 0)
        assertThat(repo.imageIds()).containsExactly(id1, id0)

        val repo2 = repo()
        assertThat(repo2.imageIds()).containsExactly(id1, id0)
    }
}
