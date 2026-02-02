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
package org.fairscan.app.ui.screens.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.collections.immutable.persistentListOf
import org.fairscan.app.R
import org.fairscan.app.THUMBNAIL_SIZE_DP
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.AppOverflowMenu
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.NewDocumentDialog
import org.fairscan.app.ui.components.isLandscape
import org.fairscan.app.ui.components.pageCountText
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.fakeDocument
import org.fairscan.app.ui.screens.settings.ExportFormat.PDF
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.theme.FairScanTheme
import java.io.File
import java.io.IOException

@Composable
fun ExportScreenWrapper(
    navigation: Navigation,
    uiState: ExportUiState,
    currentDocument: DocumentUiModel,
    pdfActions: ExportActions,
    onCloseScan: () -> Unit,
) {
    BackHandler { navigation.back() }

    val showConfirmationDialog = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        pdfActions.initializeExportScreen()
    }

    val onFilenameChange = { newName:String ->
        pdfActions.setFilename(newName)
    }

    ExportScreen(
        onFilenameChange = onFilenameChange,
        uiState = uiState,
        currentDocument = currentDocument,
        navigation = navigation,
        onShare = {
            if (!uiState.isSaving) {
                pdfActions.share()
            }
        },
        onSave = {
            if (!uiState.isSaving) {
                pdfActions.save()
            }
        },
        onOpen = pdfActions.open,
        onCloseScan = {
            if (!uiState.isSaving) {
                if (uiState.hasSavedOrShared)
                    onCloseScan()
                else
                    showConfirmationDialog.value = true
            }
        },
    )

    if (showConfirmationDialog.value) {
        NewDocumentDialog(onCloseScan, showConfirmationDialog, stringResource(R.string.end_scan))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onFilenameChange: (String) -> Unit,
    uiState: ExportUiState,
    currentDocument: DocumentUiModel,
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
            .padding(horizontal = 16.dp)
        val onThumbnailClick = navigation.toDocumentScreen
        if (!isLandscape(LocalConfiguration.current)) {
            Column(
                modifier = containerModifier.fillMaxSize().padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PdfInfosAndResultBar(uiState, currentDocument, onOpen, onThumbnailClick)
                Spacer(Modifier.weight(1f)) // push buttons down
                MainActions(onFilenameChange, uiState, onShare, onSave, onCloseScan)
            }
        } else {
            Row(
                modifier = containerModifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PdfInfosAndResultBar(uiState, currentDocument, onOpen, onThumbnailClick)
                }
                Column(modifier = Modifier.weight(1f)) {
                    MainActions(onFilenameChange, uiState, onShare, onSave, onCloseScan)
                }
            }

        }
    }
}

@Composable
private fun PdfInfosAndResultBar(
    uiState: ExportUiState,
    currentDocument: DocumentUiModel,
    onOpen: (SavedItem) -> Unit,
    onThumbnailClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        PdfInfoCard {
            PdfInfos(uiState, currentDocument, onThumbnailClick)
            SaveStatusBar(uiState, onOpen)
        }

        uiState.error?.let {
            ErrorBar(it)
        }
    }

}

@Composable
private fun PdfInfoCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PdfInfos(
    uiState: ExportUiState,
    currentDocument: DocumentUiModel,
    onThumbnailClick: () -> Unit,
) {
    val result = uiState.result

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val thumbnail = currentDocument.loadThumbnail(0)
        thumbnail?.let {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .padding(4.dp)
                    .heightIn(max = THUMBNAIL_SIZE_DP.dp)
                    .widthIn(max = THUMBNAIL_SIZE_DP.dp)
            ) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.clickable { onThumbnailClick() }
                )
            }
        }
        // PDF infos
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            if (uiState.isGenerating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringResource(R.string.creating_export),
                        fontStyle = FontStyle.Italic
                    )
                }
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
    }
}

@Composable
private fun SaveStatusBar(
    uiState: ExportUiState,
    onOpen: (SavedItem) -> Unit,
) {
    when {
        uiState.isSaving -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        uiState.savedBundle != null -> {
            SaveInfoBar(uiState.savedBundle, onOpen)
        }
    }
}

@Composable
private fun FilenameTextField(
    filename: String,
    onFilenameChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = filename,
        onValueChange = onFilenameChange,
        label = { Text(stringResource(R.string.filename)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        trailingIcon = {
            if (filename.isNotEmpty()) {
                IconButton(onClick = {
                    onFilenameChange("")
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
    onFilenameChange: (String) -> Unit,
    uiState: ExportUiState,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCloseScan: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionSurface {
            FilenameTextField(uiState.filename, onFilenameChange)

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
                        .alpha(if (uiState.isSaving) 0.6f else 1f)
                )
            }
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
private fun ActionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            content()
        }
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
        Spacer(Modifier.width(4.dp))
        Text(text)
    }
}

@Composable
private fun SaveInfoBar(
    savedBundle: SavedBundle,
    onOpen: (SavedItem) -> Unit,
) {
    val dirName = savedBundle.saveDir?.name ?: stringResource(R.string.download_dirname)
    val items = savedBundle.items
    val nbFiles = items.size
    val firstFileName = items[0].fileName

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = LocalResources.current.getQuantityString(
                        R.plurals.files_saved_to,
                        nbFiles,
                        nbFiles, firstFileName, dirName
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            if (nbFiles == 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    MainActionButton(
                        onClick = { onOpen(items[0]) },
                        text = stringResource(R.string.open),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBar(error: ExportError) {
    val (summary, details) = error.toDisplayText()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.error,
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )

            if (details != null) {
                IconButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = buildString {
                            append(summary)
                            append("\n\n")
                            append(details)
                        }
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Export error", text)
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy_logs),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (details != null) {
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ExportError.toDisplayText(): Pair<String, String?> {
    return when (this) {
        is ExportError.OnPrepare -> {
            val summary = message
            val details = throwable.message
            summary to details
        }

        is ExportError.OnSave -> {
            val summary = stringResource(messageRes)
            val contextLines = buildErrorContextLines(saveDir)
            val details = buildString {
                if (contextLines.isNotEmpty()) {
                    append(contextLines.joinToString("\n"))
                }
                throwable?.message?.let {
                    if (isNotEmpty()) append("\n\n")
                    append(it)
                }
            }.ifEmpty { null }

            summary to details
        }
    }
}

@Composable
private fun buildErrorContextLines(
    saveDir: SaveDir?,
): List<String> {
    val defaultDirName = stringResource(R.string.download_dirname)

    val folderLine = when {
        saveDir == null ->
            stringResource(R.string.error_context_folder, defaultDirName)

        saveDir.name != null ->
            stringResource(R.string.error_context_folder, saveDir.name)

        else -> null
    }

    val providerLine = saveDir?.uri?.authority
        ?.let(::providerLabel)
        ?.let { stringResource(R.string.error_context_provider, it) }

    return listOfNotNull(folderLine, providerLine)
}

fun providerLabel(authority: String): String =
    when {
        authority.contains("nextcloud", ignoreCase = true) ->
            "Nextcloud"
        authority == "com.android.externalstorage.documents" ->
            "Local storage"
        else ->
            authority
    }

fun formatFileSize(sizeInBytes: Long?, context: Context): String {
    return if (sizeInBytes == null) context.getString(R.string.unknown_size)
    else Formatter.formatShortFileSize(context, sizeInBytes)
}

@Preview
@Composable
fun PreviewExportScreenDuringGeneration() {
    ExportPreviewToCustomize(
        uiState = ExportUiState(isGenerating = true, filename = "Scan 2025-12-15 07:00:51")
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
                listOf(SavedItem(file.toUri(),  "12345.pdf", PDF))
            ),
        ),
    )
}

@Preview
@Composable
fun ExportScreenPreviewWithError() {
    ExportPreviewToCustomize(
        ExportUiState(error =
            ExportError.OnPrepare("PDF generation failed", IOException("Boom")))
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
                SaveDir("fil:///root/dir".toUri() ,"MyVeryVeryLongDirectoryName")),
        ),
    )
}

@Composable
fun ExportPreviewToCustomize(uiState: ExportUiState) {
    FairScanTheme {
        ExportScreen(
            onFilenameChange = {_->},
            uiState = uiState,
            currentDocument = fakeDocument(
                persistentListOf("gallica.bnf.fr-bpt6k5530456s-1"),
                LocalContext.current
            ),
            navigation = dummyNavigation(),
            onShare = {},
            onSave = {},
            onOpen = {},
            onCloseScan = {},
        )
    }
}
