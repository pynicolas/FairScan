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
package org.fairscan.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.variant.LibrariesDensity
import com.mikepenz.aboutlibraries.ui.compose.variant.LibrariesVariant
import org.fairscan.app.R
import org.fairscan.app.ui.components.BackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.libraries_open_source)) },
                navigationIcon = { BackButton(onClick = onBack) }
            )
        }
    ) { padding ->
        Surface(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            val librariesState by produceLibraries(R.raw.aboutlibraries)
            LibrariesContainer(
                libraries = librariesState,
                density = LibrariesDensity.Compact,
                variant = LibrariesVariant.Refined,
            )
        }
    }
}

