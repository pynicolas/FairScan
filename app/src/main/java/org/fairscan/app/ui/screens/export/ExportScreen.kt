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
package org.fairscan.app.ui.screens.export

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.fairscan.app.R
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.AppOverflowMenu
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.NewDocumentDialog
import org.fairscan.app.ui.components.isLandscape
import org.fairscan.app.ui.components.pageCountText
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.screens.settings.ExportFormat.PDF
import org.fairscan.app.ui.theme.FairScanTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportScreenWrapper(
    navigation: Navigation,
    uiState: ExportUiState,
    pdfActions: ExportActions,
    onCloseScan: () -> Unit,
) {
    BackHandler { navigation.back() }

    val showConfirmationDialog = rememberSaveable { mutableStateOf(false) }
    val filename = remember { mutableStateOf(defaultFilename()) }
    LaunchedEffect(Unit) {
        pdfActions.setFilename(filename.value)
        pdfActions.initializeExportScreen()
    }

    val onFilenameChange = { newName:String ->
        filename.value = newName
        pdfActions.setFilename(newName)
    }
    val ensureCorrectFileName = {
        val value = filename.value.trim().ifEmpty { defaultFilename() }
        if (value != filename.value) {
            onFilenameChange(value)
        }
    }

    ExportScreen(
        filename = filename,
        onFilenameChange = onFilenameChange,
        uiState = uiState,
        navigation = navigation,
        onShare = {
            ensureCorrectFileName()
            pdfActions.share()
        },
        onSave = {
            ensureCorrectFileName()
            pdfActions.save()
        },
        onOpen = pdfActions.open,
        onCloseScan = {
            if (uiState.hasSavedOrShared)
                onCloseScan()
            else
                showConfirmationDialog.value = true
        },
    )

    if (showConfirmationDialog.value) {
        NewDocumentDialog(onCloseScan, showConfirmationDialog, stringResource(R.string.end_scan))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    filename: MutableState<String>,
    onFilenameChange: (String) -> Unit,
    uiState: ExportUiState,
    navigation: Navigation,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onOpen: (SavedItem) -> Unit,
    onCloseScan: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_as, uiState.format)) },
                navigationIcon = { BackButton(navigation.back) },
                actions = {
                    AppOverflowMenu(navigation)
                }
            )
        }
    ) { innerPadding ->
        val containerModifier = Modifier
            .padding(innerPadding)
            .padding(16.dp)
        if (!isLandscape(LocalConfiguration.current)) {
            Column(
                modifier = containerModifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextFieldAndPdfInfos(filename, onFilenameChange, uiState, onOpen)
                Spacer(Modifier.weight(1f)) // push buttons down
                MainActions(uiState, onShare, onSave, onCloseScan)
            }
        } else {
            Row(
                modifier = containerModifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextFieldAndPdfInfos(filename, onFilenameChange, uiState, onOpen)
                }
                Column(modifier = Modifier.weight(1f)) {
                    MainActions(uiState, onShare, onSave, onCloseScan)
                }
            }

        }
    }
}

@Composable
private fun TextFieldAndPdfInfos(
    filename: MutableState<String>,
    onFilenameChange: (String) -> Unit,
    uiState: ExportUiState,
    onOpen: (SavedItem) -> Unit,
) {
    FilenameTextField(filename, onFilenameChange)

    val result = uiState.result

    // PDF infos
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        if (uiState.isGenerating) {
            Text(stringResource(R.string.creating_export), fontStyle = FontStyle.Italic)
        } else if (result != null) {
            val context = LocalContext.current
            val formattedFileSize = formatFileSize(result.sizeInBytes, context)
            Text(text = pageCountText(result.pageCount))
            val sizeMessageKey =
                if (result.files.size == 1) R.string.file_size else R.string.file_size_total
            Text(
                text = stringResource(sizeMessageKey, formattedFileSize),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    if (uiState.savedBundle != null) {
        SaveInfoBar(uiState.savedBundle, onOpen)
    }
    if (uiState.errorMessage != null) {
        ErrorBar(uiState.errorMessage)
    }
}

@Composable
private fun FilenameTextField(
    filename: MutableState<String>,
    onFilenameChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = filename.value,
        onValueChange = onFilenameChange,
        label = { Text(stringResource(R.string.filename)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        trailingIcon = {
            if (filename.value.isNotEmpty()) {
                IconButton(onClick = {
                    filename.value = ""
                    focusRequester.requestFocus()
                }) {
                    Icon(Icons.Default.Clear, stringResource(R.string.clear_text))
                }
            }
        },
    )
}

@Composable
private fun MainActions(
    uiState: ExportUiState,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCloseScan: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ExportButton(
                onClick = onShare,
                enabled = uiState.result != null,
                isPrimary = !uiState.hasSavedOrShared,
                icon = Icons.Default.Share,
                text = stringResource(R.string.share),
                modifier = Modifier.weight(1f)
            )
            ExportButton(
                onClick = onSave,
                enabled = uiState.result != null,
                isPrimary = !uiState.hasSavedOrShared,
                icon = Icons.Default.Download,
                text = stringResource(R.string.save),
                modifier = Modifier.weight(1f)
            )
        }
        ExportButton(
            icon = Icons.Default.Done,
            text = stringResource(R.string.end_scan),
            onClick = onCloseScan,
            modifier = Modifier.fillMaxWidth(),
            isPrimary = uiState.hasSavedOrShared,
        )
    }
}

@Composable
fun ExportButton(
    icon: ImageVector,
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isPrimary) MaterialTheme.colorScheme.primary
        else Color.Transparent
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPrimary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.primary
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPrimary) Color.Transparent
        else MaterialTheme.colorScheme.primary
    )

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.dp, borderColor),
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun SaveInfoBar(savedBundle: SavedBundle, onOpen: (SavedItem) -> Unit) {
    val dirName = savedBundle.exportDirName?:stringResource(R.string.download_dirname)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 16.dp),
    ) {
        val items = savedBundle.items
        val nbFiles = items.size
        val firstFileName = items[0].fileName
        Text(
            text = LocalResources.current.getQuantityString(
                R.plurals.files_saved_to,
                nbFiles,
                nbFiles, firstFileName, dirName
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (nbFiles == 1) {
            Spacer(Modifier.width(8.dp))
            MainActionButton(
                onClick = { onOpen(items[0]) },
                text = stringResource(R.string.open),
                icon = Icons.AutoMirrored.Filled.OpenInNew,
            )
        }
    }
}

@Composable
private fun ErrorBar(errorMessage: String) {
    Text(
        text = stringResource(R.string.error, errorMessage),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
    )
}

fun defaultFilename(): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault()).format(Date())
    return "Scan $timestamp"
}

fun formatFileSize(sizeInBytes: Long?, context: Context): String {
    return if (sizeInBytes == null) context.getString(R.string.unknown_size)
    else Formatter.formatShortFileSize(context, sizeInBytes)
}

@Preview
@Composable
fun PreviewExportScreenDuringGeneration() {
    ExportPreviewToCustomize(
        uiState = ExportUiState(isGenerating = true)
    )
}

@Preview
@Composable
fun PreviewExportScreenAfterGeneration() {
    val file = File("fake.pdf")
    ExportPreviewToCustomize(
        uiState = ExportUiState(
            result = ExportResult.Pdf(file, 442897L, 3),
        ),
    )
}

@Preview
@Composable
fun PreviewExportScreenAfterSave() {
    val file = File("fake.pdf")
    ExportPreviewToCustomize(
        uiState = ExportUiState(
            result = ExportResult.Pdf(file, 442897L, 3),
            savedBundle = SavedBundle(
                listOf(SavedItem(file.toUri(), defaultFilename() + ".pdf", PDF))
            ),
        ),
    )
}

@Preview
@Composable
fun ExportScreenPreviewWithError() {
    ExportPreviewToCustomize(
        ExportUiState(errorMessage = "PDF generation failed")
    )
}

@Preview(showBackground = true, widthDp = 720, heightDp = 360)
@Composable
fun PreviewExportScreenAfterSaveHorizontal() {
    val file = File("fake.pdf")
    ExportPreviewToCustomize(
        uiState = ExportUiState(
            result = ExportResult.Pdf(file, 442897L, 3),
            savedBundle = SavedBundle(
                listOf(SavedItem(file.toUri(), "my_file.pdf", PDF)),
                exportDirName="MyVeryVeryLongDirectoryName"),
        ),
    )
}

@Composable
fun ExportPreviewToCustomize(uiState: ExportUiState) {
    FairScanTheme {
        ExportScreen(
            filename = remember { mutableStateOf("Scan 2025-07-02 17.40.42") },
            onFilenameChange = {_->},
            navigation = dummyNavigation(),
            uiState = uiState,
            onShare = {},
            onSave = {},
            onOpen = {},
            onCloseScan = {},
        )
    }
}
