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

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mydomain.myscan.ui.theme.MyScanTheme
import org.mydomain.myscan.view.CameraScreen
import org.mydomain.myscan.view.FinalizeDocumentScreen
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initLibraries()
        val viewModel: MainViewModel by viewModels { MainViewModel.getFactory(this) }
        enableEdgeToEdge()
        setContent {
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val liveAnalysisState by viewModel.liveAnalysisState.collectAsStateWithLifecycle()
            val pageIds by viewModel.pageIds.collectAsStateWithLifecycle()
            val context = LocalContext.current
            MyScanTheme {
                Scaffold { innerPadding ->
                    Column (modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            is Screen.Camera -> {
                                CameraScreen(viewModel, liveAnalysisState,
                                    onImageAnalyzed = { image -> viewModel.segment(image) },
                                    onFinalizePressed = { viewModel.navigateTo(Screen.FinalizeDocument) }
                                )
                            }
                            is Screen.FinalizeDocument -> {
                                FinalizeDocumentScreen (
                                    pageIds,
                                    imageLoader = { id -> viewModel.getBitmap(id) },
                                    onBackPressed = { viewModel.navigateTo(Screen.Camera) },
                                    onSavePressed = savePdf(viewModel, context),
                                    onSharePressed = sharePdf(viewModel, context),
                                    onDeleteImage =  { id -> viewModel.deletePage(id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sharePdf(
        viewModel: MainViewModel,
        context: Context
    ): () -> Unit = {
        val outputDir = File(cacheDir, "pdfs").apply { mkdirs() }
        val outputFile = File(outputDir, "scan_${System.currentTimeMillis()}.pdf")
        var success = true
        try {
            val fileOutputStream = FileOutputStream(outputFile)
            viewModel.createPdf(fileOutputStream)
        } catch (_: IOException) {
            Toast.makeText(context, "Failed to share PDF", Toast.LENGTH_SHORT).show()
            success = false
        }
        if (success) {
            val uri = FileProvider.getUriForFile(
                context,
                "${applicationContext.packageName}.fileprovider",
                outputFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        }
    }

    private fun savePdf(
        viewModel: MainViewModel,
        context: Context
    ): () -> Unit = {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, "scan_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            viewModel.createPdf(outputStream)
            outputStream.flush()
            outputStream.close()

            MediaScannerConnection.scanFile(
                context, arrayOf(file.absolutePath), arrayOf("application/pdf"), null
            )

            Toast.makeText(context, "Saved PDF in Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MyScan", "Failed to save PDF", e)
            Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
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
