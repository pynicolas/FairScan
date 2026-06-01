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
import org.junit.Test
import java.util.Locale
import java.util.Locale.ENGLISH
import java.util.Locale.FRENCH
import java.util.Locale.GERMAN

class OcrLanguageTest {

    @Test
    fun `specific display names`() {
        assertThat(name("eng", ENGLISH)).isEqualTo("English")
        assertThat(name("eng", FRENCH)).isEqualTo("Anglais")
        assertThat(name("fra", ENGLISH)).isEqualTo("French")
        assertThat(name("chi_sim_vert", ENGLISH)).isEqualTo("Chinese (China, vertical)")
        assertThat(name("deu_latf", GERMAN)).isEqualTo("Deutsch (Alte deutsche Rechtschreibung)")
        assertThat(name("aze_cyrl", ENGLISH)).isEqualTo("Azerbaijani (Cyrillic)")
    }

    @Test
    fun `all available codes resolve to a display name different from the code`() {
        val failing = OcrLanguage.AVAILABLE_LANGUAGE_CODES
            .map { code -> code to name(code, ENGLISH) }
            .filter { (code, name) -> name == code }
            .map { (code, _) -> code }
        assertThat(failing)
            .describedAs("These codes did not resolve to a proper display name")
            .isEmpty()
    }

    @Test
    fun `all available codes produce unique display names`() {
        val names = OcrLanguage.AVAILABLE_LANGUAGE_CODES
            .map { code -> name(code, ENGLISH) }
        assertThat(names).doesNotHaveDuplicates()
    }

    private fun name(code: String, displayLocale: Locale)
        = OcrLanguage(code).displayName(displayLocale)
}
