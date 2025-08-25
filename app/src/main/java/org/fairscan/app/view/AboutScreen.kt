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
package org.fairscan.app.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fairscan.app.BuildConfig
import org.fairscan.app.R
import org.fairscan.app.ui.theme.MyScanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onViewLibraries: () -> Unit) {
    val showLicenseDialog = rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = { BackButton(onBack) },
            )
        }
    ) { paddingValues ->
        AboutContent(modifier = Modifier.padding(paddingValues), showLicenseDialog, onViewLibraries)
    }
    if (showLicenseDialog.value) {
        LicenseBottomSheet(sheetState, onDismiss = { showLicenseDialog.value = false })
    }
}

@Composable
fun AboutContent(
    modifier: Modifier = Modifier,
    showLicenseDialog: MutableState<Boolean>,
    onViewLibraries: () -> Unit,
    ) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.version),
            style = MaterialTheme.typography.titleMedium
        )
        Text(BuildConfig.VERSION_NAME)

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.license),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.licensed_under),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.view_the_full_license),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { showLicenseDialog.value = true },
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.libraries),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.libraries_intro) +
                    "\n• CameraX\n• Jetpack Compose\n• LiteRT\n• OpenCV\n• PDFBox",
            style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.view_full_list),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(onClick = onViewLibraries),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val licenseText by remember {
        mutableStateOf(
            context.resources.openRawResource(R.raw.gpl3)
                .bufferedReader()
                .use { it.readText() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "GNU General Public License v3.0",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        HorizontalDivider()

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp) // TODO check if it's ok
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = licenseText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Preview
@Composable
fun AboutScreenPreview() {
    MyScanTheme {
        AboutScreen(onBack = {}, onViewLibraries = {})
    }
}