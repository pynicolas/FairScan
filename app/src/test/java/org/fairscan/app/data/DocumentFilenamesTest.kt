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
package org.fairscan.app.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentFilenamesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun sameNameNeverOverwritesAnExistingDocument() {
        val original = temp.newFile("scan.pdf").apply { writeText("original document") }
        val second = availableDocumentFile(temp.root, "scan.pdf").apply { writeText("second document") }
        val third = availableDocumentFile(temp.root, "scan.pdf")
        assertThat(second.name).isEqualTo("scan (1).pdf")
        assertThat(third.name).isEqualTo("scan (2).pdf")
        assertThat(original).hasContent("original document")
        assertThat(second).hasContent("second document")
    }

    @Test fun filenameCannotEscapeTheLibraryFolder() {
        for (name in listOf("../../secret.pdf", "..\\..\\secret.pdf", "/outside.pdf", "C:\\outside.pdf")) {
            assertThat(availableDocumentFile(temp.root, name).canonicalFile.parentFile)
                .isEqualTo(temp.root.canonicalFile)
        }
    }

    @Test fun emptyAndHiddenNamesHaveAVisibleFallback() {
        assertThat(safeDocumentName(" ... ")).isEqualTo("FairScan")
        assertThat(safeDocumentName("  ")).isEqualTo("FairScan")
    }

    @Test fun unicodeNamesKeepTheirExtensionAndFitTheFilesystemLimit() {
        val name = safeDocumentName("\uD83D\uDCC4".repeat(100) + ".pdf")
        assertThat(name).endsWith(".pdf")
        assertThat(name.toByteArray(Charsets.UTF_8).size).isLessThan(255)
        assertThat(safeDocumentName(name)).isEqualTo(name)
        assertThat(name).doesNotContain("\uFFFD")
    }
}
