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

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.fairscan.app.GeneratedPdf
import org.fairscan.app.PdfGenerationActions
import org.fairscan.app.R
import org.fairscan.app.ui.PdfGenerationUiState
import org.fairscan.app.ui.theme.MyScanTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfGenerationBottomSheetWrapper(
    onDismiss: () -> Unit,
    pdfActions: PdfGenerationActions,
    modifier: Modifier = Modifier,
) {
    var filename by remember { mutableStateOf(defaultFilename()) }
    val uiState by pdfActions.uiStateFlow.collectAsState()
    LaunchedEffect(Unit) {
        pdfActions.setFilename(filename)
        pdfActions.startGeneration()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) {
        sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.navigationBarsPadding()
    ) {
        PdfGenerationBottomSheet(
            filename = filename,
            onFilenameChange = {
                filename = it
                pdfActions.setFilename(it)
            },
            uiState = uiState,
            onDismiss = {
                pdfActions.cancelGeneration()
                onDismiss()
            },
            onShare = { pdfActions.sharePdf() },
            onSave = { pdfActions.savePdf() },
            onOpen = { pdfActions.openPdf() },
        )
    }
}

@Composable
fun PdfGenerationBottomSheet(
    filename: String,
    onFilenameChange: (String) -> Unit,
    uiState: PdfGenerationUiState,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    Column() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row (verticalAlignment = Alignment.CenterVertically) {
                Row {
                    Icon(
                        Icons.Default.PictureAsPdf, contentDescription = "PDF",
                        modifier = Modifier
                            .size(34.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        stringResource(R.string.export_pdf),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                CloseButton(onDismiss)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = filename,
                onValueChange = onFilenameChange,
                label = { Text(stringResource(R.string.filename)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            val pdf = uiState.generatedPdf
            if (uiState.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (pdf != null) {
                val context = LocalContext.current
                val formattedFileSize = formatFileSize(pdf.sizeInBytes, context)
                Text(
                    text = "${pageCountText(pdf.pageCount)} · $formattedFileSize",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(24.dp))

            MainActions(pdf, onShare, onSave)
        }

        if (uiState.saveDirectoryName != null) {
            SavePdfBar(onOpen, uiState.saveDirectoryName)
        }
        if (uiState.errorMessage != null) {
            ErrorBar(uiState.errorMessage)
        }
    }
}

@Composable
private fun MainActions(
    pdf: GeneratedPdf?,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MainActionButton(
            onClick = onShare,
            enabled = pdf != null,
            icon = Icons.Default.Share,
            iconDescription = stringResource(R.string.share),
            text = stringResource(R.string.share),
            modifier = Modifier.weight(1f)
        )
        MainActionButton(
            onClick = onSave,
            enabled = pdf != null,
            icon = Icons.Default.Download,
            iconDescription = stringResource(R.string.save),
            text = stringResource(R.string.save),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SavePdfBar(onOpen: () -> Unit, saveDirectoryName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.pdf_saved_to, saveDirectoryName),
            style = MaterialTheme.typography.bodyMedium
        )
        MainActionButton(
            onClick = onOpen,
            text = stringResource(R.string.open),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        )
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

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close)
            )
        }
    }
}

fun defaultFilename(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return "scan_$timestamp.pdf"
}

fun formatFileSize(sizeInBytes: Long?, context: Context): String {
    return if (sizeInBytes == null) context.getString(R.string.unknown_size)
    else Formatter.formatShortFileSize(context, sizeInBytes)
}

@Preview(showBackground = true)
@Composable
fun PreviewPdfGenerationDialogDuringGeneration() {
    PreviewToCustomize(
        uiState = PdfGenerationUiState(isGenerating = true)
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewPdfGenerationDialogAfterGeneration() {
    PreviewToCustomize(
        uiState = PdfGenerationUiState(
            generatedPdf = GeneratedPdf(File("fake.pdf"), 442897L, 1)
        )
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewPdfGenerationDialogAfterSave() {
    val file = File("fake.pdf")
    PreviewToCustomize(
        uiState = PdfGenerationUiState(
            generatedPdf = GeneratedPdf(file, 442897L, 3),
            savedFileUri = file.toUri()
        )
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewPdfGenerationDialogWithError() {
    PreviewToCustomize(
        uiState = PdfGenerationUiState(
            errorMessage = "PDF generation failed"
        )
    )
}

@Composable
fun PreviewToCustomize(uiState: PdfGenerationUiState) {
    MyScanTheme {
        PdfGenerationBottomSheet(
            filename = "scan_20250702_174042.pdf",
            uiState = uiState,
            onFilenameChange = {},
            onDismiss = {},
            onShare = {},
            onSave = {},
            onOpen = {},
        )
    }
}
