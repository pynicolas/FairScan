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
package org.fairscan.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fairscan.app.data.GeneratedPdf
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.components.rememberCameraPermissionState
import org.fairscan.app.ui.screens.DocumentScreen
import org.fairscan.app.ui.screens.LibrariesScreen
import org.fairscan.app.ui.screens.about.AboutEvent
import org.fairscan.app.ui.screens.about.AboutScreen
import org.fairscan.app.ui.screens.about.AboutViewModel
import org.fairscan.app.ui.screens.camera.CameraEvent
import org.fairscan.app.ui.screens.camera.CameraScreen
import org.fairscan.app.ui.screens.camera.CameraViewModel
import org.fairscan.app.ui.screens.export.ExportEvent
import org.fairscan.app.ui.screens.export.ExportScreenWrapper
import org.fairscan.app.ui.screens.export.ExportViewModel
import org.fairscan.app.ui.screens.export.PdfGenerationActions
import org.fairscan.app.ui.screens.home.HomeScreen
import org.fairscan.app.ui.screens.home.HomeViewModel
import org.fairscan.app.ui.screens.settings.SettingsScreen
import org.fairscan.app.ui.screens.settings.SettingsViewModel
import org.fairscan.app.ui.theme.FairScanTheme
import org.opencv.android.OpenCVLoader

private const val PDF_MIME_TYPE = "application/pdf"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initLibraries()
        val appContainer = (application as FairScanApp).appContainer
        val viewModel: MainViewModel by viewModels { appContainer.mainViewModelFactory }
        val homeViewModel: HomeViewModel by viewModels { appContainer.homeViewModelFactory }
        val cameraViewModel: CameraViewModel by viewModels { appContainer.cameraViewModelFactory }
        val exportViewModel: ExportViewModel by viewModels { appContainer.exportViewModelFactory }
        val aboutViewModel: AboutViewModel by viewModels { appContainer.aboutViewModelFactory }
        val settingsViewModel: SettingsViewModel
            by viewModels { appContainer.settingsViewModelFactory }
        lifecycleScope.launch(Dispatchers.IO) {
            exportViewModel.cleanUpOldPdfs(1000 * 3600)
        }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val liveAnalysisState by cameraViewModel.liveAnalysisState.collectAsStateWithLifecycle()
            val document by viewModel.documentUiModel.collectAsStateWithLifecycle()
            val cameraPermission = rememberCameraPermissionState()
            CollectCameraEvents(cameraViewModel, viewModel)
            CollectExportEvents(context, exportViewModel, homeViewModel)
            CollectAboutEvents(context, aboutViewModel)

            FairScanTheme {
                val navigation = navigation(viewModel)
                when (val screen = currentScreen) {
                    is Screen.Main.Home -> {
                        val recentDocs by homeViewModel.recentDocuments.collectAsStateWithLifecycle()
                        HomeScreen(
                            cameraPermission = cameraPermission,
                            currentDocument = document,
                            navigation = navigation,
                            onClearScan = { viewModel.startNewDocument() },
                            recentDocuments = recentDocs,
                            onOpenPdf = { file -> openPdf(file.toUri()) }
                        )
                    }
                    is Screen.Main.Camera -> {
                        CameraScreen(
                            viewModel,
                            cameraViewModel,
                            navigation,
                            liveAnalysisState,
                            onImageAnalyzed = { image -> cameraViewModel.liveAnalysis(image) },
                            onFinalizePressed = navigation.toDocumentScreen,
                            cameraPermission = cameraPermission
                        )
                    }
                    is Screen.Main.Document -> {
                        DocumentScreen (
                            document = document,
                            initialPage = screen.initialPage,
                            navigation = navigation,
                            onDeleteImage =  { id -> viewModel.deletePage(id) },
                            onRotateImage = { id, clockwise -> viewModel.rotateImage(id, clockwise) },
                            onPageReorder = { id, newIndex -> viewModel.movePage(id, newIndex) },
                        )
                    }
                    is Screen.Main.Export -> {
                        ExportScreenWrapper(
                            navigation = navigation,
                            pdfActions = PdfGenerationActions(
                                startGeneration = exportViewModel::startPdfGeneration,
                                setFilename = exportViewModel::setFilename,
                                uiStateFlow = exportViewModel.pdfUiState,
                                sharePdf = { sharePdf(exportViewModel.getFinalPdf(), exportViewModel) },
                                savePdf = { exportViewModel.onSavePdfClicked() },
                                openPdf = { openPdf(exportViewModel.pdfUiState.value.savedFileUri) }
                            ),
                            onCloseScan = {
                                viewModel.startNewDocument()
                                viewModel.navigateTo(Screen.Main.Home)
                            },
                        )
                    }
                    is Screen.Overlay.About -> {
                        AboutScreen(
                            onBack = navigation.back,
                            onCopyLogs = { aboutViewModel.onCopyLogsClicked() },
                            onViewLibraries = navigation.toLibrariesScreen)
                    }
                    is Screen.Overlay.Libraries -> {
                        LibrariesScreen(onBack = navigation.back)
                    }
                    is Screen.Overlay.Settings -> {
                        SettingsScreenWrapper(settingsViewModel, navigation)
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsScreenWrapper(settingsViewModel: SettingsViewModel, nav: Navigation) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
                settingsViewModel.setExportDirUri(uri.toString())
            }
        }
        val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        SettingsScreen(
            settingsUiState,
            onChooseDirectoryClick = { launcher.launch(null) },
            onResetExportDirClick = { settingsViewModel.setExportDirUri(null) },
            onBack = nav.back
        )
    }

    @Composable
    private fun CollectAboutEvents(
        context: Context,
        aboutViewModel: AboutViewModel,
    ) {
        val clipboard = LocalClipboard.current
        val msgCopiedLogs = stringResource(R.string.copied_logs)
        LaunchedEffect(Unit) {
            aboutViewModel.events.collect { event ->
                when (event) {
                    is AboutEvent.CopyLogs -> {
                        clipboard.setClipEntry(
                            ClipData.newPlainText("FairScan logs", event.logs).toClipEntry()
                        )
                        Toast.makeText(context, msgCopiedLogs, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @Composable
    private fun CollectExportEvents(
        context: Context,
        exportViewModel: ExportViewModel,
        homeViewModel: HomeViewModel,
    ) {
        val storagePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                exportViewModel.onSavePdfClicked()
            } else {
                val message = getString(R.string.storage_permission_denied)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
        LaunchedEffect(Unit) {
            exportViewModel.events.collect { event ->
                when (event) {
                    ExportEvent.RequestSavePdf -> {
                        checkPermissionThen(storagePermissionLauncher) {
                            exportViewModel.onRequestPdfSave(context, homeViewModel)
                        }
                    }

                    is ExportEvent.SaveError -> {
                        val text = getString(R.string.error_save)
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @Composable
    private fun CollectCameraEvents(
        cameraViewModel: CameraViewModel,
        viewModel: MainViewModel,
    ) {
        LaunchedEffect(Unit) {
            cameraViewModel.events.collect { event ->
                when (event) {
                    is CameraEvent.ImageCaptured -> viewModel.handleImageCaptured(event.jpegBytes)
                }
            }
        }
    }

    private fun sharePdf(generatedPdf: GeneratedPdf?, viewModel: ExportViewModel) {
        if (generatedPdf == null)
            return
        viewModel.setPdfAsShared()
        val file = generatedPdf.file
        val authority = "${applicationContext.packageName}.fileprovider"
        val fileUri = FileProvider.getUriForFile(this, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, getString(R.string.share_pdf))
        val resInfoList = packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
        for (resInfo in resInfoList) {
            val packageName = resInfo.activityInfo.packageName
            grantUriPermission(packageName, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(chooser)
    }

    private fun checkPermissionThen(
        requestPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
        action: () -> Unit
    ) {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (SDK_INT < Q && checkSelfPermission(this, permission) != PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(permission)
        } else {
            action()
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
            startActivity(Intent.createChooser(openIntent, getString(R.string.open_pdf)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.error_no_pdf_app), Toast.LENGTH_SHORT).show()
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

private fun navigation(viewModel: MainViewModel): Navigation = Navigation(
    toHomeScreen = { viewModel.navigateTo(Screen.Main.Home) },
    toCameraScreen = { viewModel.navigateTo(Screen.Main.Camera) },
    toDocumentScreen = { viewModel.navigateTo(Screen.Main.Document()) },
    toExportScreen = { viewModel.navigateTo(Screen.Main.Export) },
    toAboutScreen = { viewModel.navigateTo(Screen.Overlay.About) },
    toLibrariesScreen = { viewModel.navigateTo(Screen.Overlay.Libraries) },
    toSettingsScreen = { viewModel.navigateTo(Screen.Overlay.Settings) },
    back = { viewModel.navigateBack() }
)
