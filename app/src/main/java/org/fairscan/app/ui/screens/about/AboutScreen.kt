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
package org.fairscan.app.ui.screens.about

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.fairscan.app.BuildConfig
import org.fairscan.app.R
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.theme.FairScanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    aboutUiState: AboutUiState,
    onBack: () -> Unit,
    onCopyLogs: () -> Unit,
    onContactWithLastImageClicked: () -> Unit,
    onViewLibraries: () -> Unit,
) {
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
        AboutContent(
            modifier = Modifier.padding(paddingValues),
            aboutUiState,
            onCopyLogs,
            onContactWithLastImageClicked,
            showLicenseDialog,
            onViewLibraries)
    }
    if (showLicenseDialog.value) {
        LicenseBottomSheet(sheetState, onDismiss = { showLicenseDialog.value = false })
    }
}

@Composable
fun AboutContent(
    modifier: Modifier = Modifier,
    aboutUiState: AboutUiState,
    onCopyLogs: () -> Unit,
    onContactWithLastImageClicked: () -> Unit,
    showLicenseDialog: MutableState<Boolean>,
    onViewLibraries: () -> Unit,
    ) {

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.icon),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.app_tagline),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Section(title = stringResource(R.string.version)) {
            Text(BuildConfig.VERSION_NAME)
        }

        Section(title = stringResource(R.string.developer)) {
            Text("Pierre-Yves Nicolas")
        }

        Section(title = stringResource(R.string.contact)) {
            ContactLink(
                icon = Icons.Default.Email,
                text = EMAIL_ADDRESS,
                onClick = { context.startActivity(createContactEmailIntent()) }
            )
            val websiteUrl = "https://fairscan.org"
            ContactLink(
                icon = Icons.Default.Language,
                text = websiteUrl,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, websiteUrl.toUri())
                    context.startActivity(intent)
                }
            )
        }

        Section(title = stringResource(R.string.support)) {
            EmailImageButton(aboutUiState, onContactWithLastImageClicked)
            CopyLogsButton (onClick = onCopyLogs)
        }

        Section(title = stringResource(R.string.license)) {
            Text(
                stringResource(R.string.licensed_under),
            )
            Text(
                text = stringResource(R.string.view_the_full_license),
                modifier = Modifier.clickable { showLicenseDialog.value = true },
                color = MaterialTheme.colorScheme.primary
            )
        }

        Section(title = stringResource(R.string.libraries)) {
            Text(
                text = stringResource(R.string.view_full_list),
                modifier = Modifier.clickable(onClick = onViewLibraries),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ContactLink(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val licenseText by remember {
        mutableStateOf(
            resources.openRawResource(R.raw.gpl3)
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

@Composable
fun CopyLogsButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(stringResource(R.string.copy_logs))
    }
}

@Composable
fun EmailImageButton(
    aboutUiState: AboutUiState,
    onContactWithLastImageClicked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = aboutUiState.hasLastCapturedImage,
                onClick = onContactWithLastImageClicked
            )
            .alpha(if (aboutUiState.hasLastCapturedImage) 1f else 0.5f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Email, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.support_last_image))
    }
}

@Preview
@Composable
fun AboutScreenPreview() {
    FairScanTheme {
        val state = AboutUiState(true)
        AboutScreen(state, {}, {}, {}, {})
    }
}