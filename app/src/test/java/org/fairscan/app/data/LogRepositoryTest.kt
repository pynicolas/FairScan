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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LogRepositoryTest {

    @get:Rule
    var folder: TemporaryFolder = TemporaryFolder()

    @Test
    fun log_with_exception() {
        val repo = LogRepository(folder.newFile())
        assertThat(repo.getLogs()).isEmpty()
        repo.log("tag1", "message1", IllegalArgumentException("my exception"))
        assertThat(repo.getLogs()).contains("[tag1] message1")
        assertThat(repo.getLogs()).contains("my exception")
        print(repo.getLogs())
    }

    @Test
    fun get_logs_with_file_not_yet_created() {
        val file = File(folder.newFolder(), "log.txt")
        val repo = LogRepository(file)
        assertThat(file).doesNotExist()
        assertThat(repo.getLogs()).isEmpty()
    }
}