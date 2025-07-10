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
package org.mydomain.myscan

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mydomain.myscan.ui.theme.MyScanTheme
import org.mydomain.myscan.view.AboutScreen
import org.mydomain.myscan.view.CameraScreen
import org.mydomain.myscan.view.DocumentScreen
import org.mydomain.myscan.view.LibrariesScreen
import org.opencv.android.OpenCVLoader
import java.io.File

private const val PDF_MIME_TYPE = "application/pdf"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initLibraries()
        val viewModel: MainViewModel by viewModels { MainViewModel.getFactory(this) }
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.cleanUpOldPdfs(1000 * 3600)
        }
        enableEdgeToEdge()
        setContent {
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val liveAnalysisState by viewModel.liveAnalysisState.collectAsStateWithLifecycle()
            val pageIds by viewModel.pageIds.collectAsStateWithLifecycle()
            MyScanTheme {
                val navigation = Navigation(
                    toCameraScreen = { viewModel.navigateTo(Screen.Camera) },
                    toDocumentScreen = { viewModel.navigateTo(Screen.Document()) },
                    toAboutScreen = { viewModel.navigateTo(Screen.About) },
                    toLibrariesScreen = { viewModel.navigateTo(Screen.Libraries) },
                    back = { viewModel.navigateBack() }
                )
                when (val screen = currentScreen) {
                    is Screen.Camera -> {
                        CameraScreen(
                            viewModel,
                            liveAnalysisState,
                            onImageAnalyzed = { image -> viewModel.liveAnalysis(image) },
                            onFinalizePressed = { viewModel.navigateTo(Screen.Document()) },
                        )
                    }
                    is Screen.Document -> {
                        DocumentScreen (
                            pageIds,
                            initialPage = screen.initialPage,
                            imageLoader = { id -> viewModel.getBitmap(id) },
                            navigation = navigation,
                            pdfActions = PdfGenerationActions(
                                startGeneration = viewModel::startPdfGeneration,
                                cancelGeneration = viewModel::cancelPdfGeneration,
                                setFilename = viewModel::setFilename,
                                uiStateFlow = viewModel.pdfUiState,
                                sharePdf = { sharePdf(viewModel.getFinalPdf()) },
                                savePdf = { savePdf(viewModel.getFinalPdf(), viewModel) },
                                openPdf = { openPdf(viewModel.pdfUiState.value.savedFileUri) }
                            ),
                            onStartNew = {
                                viewModel.startNewDocument()
                                viewModel.navigateTo(Screen.Camera) },
                            onDeleteImage =  { id -> viewModel.deletePage(id) }
                        )
                    }
                    is Screen.About -> {
                        AboutScreen(onBack = navigation.back, onViewLibraries = navigation.toLibrariesScreen)
                    }
                    is Screen.Libraries -> {
                        LibrariesScreen(onBack = navigation.back)
                    }
                }
            }
        }
    }

    private fun sharePdf(generatedPdf: GeneratedPdf?) {
        if (generatedPdf == null)
            return
        val file = generatedPdf.file
        val authority = "${applicationContext.packageName}.fileprovider"
        val fileUri = FileProvider.getUriForFile(this, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share PDF")
        val resInfoList = packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
        for (resInfo in resInfoList) {
            val packageName = resInfo.activityInfo.packageName
            grantUriPermission(packageName, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(chooser)
    }

    private fun savePdf(generatedPdf: GeneratedPdf?, viewModel: MainViewModel) {
        if (generatedPdf == null)
            return
        val appScope = CoroutineScope(Dispatchers.IO)
        val context = this
        appScope.launch {
            try {
                val targetFile = viewModel.saveFile(generatedPdf.file)

                suspendCancellableCoroutine { continuation ->
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(PDF_MIME_TYPE)
                    ) { _, _ -> continuation.resume(Unit) {} }
                }
            } catch (e: Exception) {
                Log.e("MyScan", "Failed to save PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openPdf(fileUri: Uri?) {
        if (fileUri == null) return
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            fileUri.toFile()
        )
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(openIntent, "Open PDF"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app found to open PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initLibraries() {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "Initialization failed")
        } else {
            Log.d("OpenCV", "Initialization successful")
        }
    }
}
