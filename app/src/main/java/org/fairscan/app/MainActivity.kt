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
package org.fairscan.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
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
import androidx.core.net.toFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fairscan.app.data.FileLogger
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.components.rememberCameraPermissionState
import org.fairscan.app.ui.screens.DocumentScreen
import org.fairscan.app.ui.screens.LibrariesScreen
import org.fairscan.app.ui.screens.about.AboutEvent
import org.fairscan.app.ui.screens.about.AboutScreen
import org.fairscan.app.ui.screens.about.AboutViewModel
import org.fairscan.app.ui.screens.about.createEmailWithImageIntent
import org.fairscan.app.ui.screens.camera.CameraEvent
import org.fairscan.app.ui.screens.camera.CameraScreen
import org.fairscan.app.ui.screens.camera.CameraViewModel
import org.fairscan.app.ui.screens.export.ExportActions
import org.fairscan.app.ui.screens.export.ExportEvent
import org.fairscan.app.ui.screens.export.ExportResult
import org.fairscan.app.ui.screens.export.ExportScreenWrapper
import org.fairscan.app.ui.screens.export.ExportViewModel
import org.fairscan.app.ui.screens.home.HomeScreen
import org.fairscan.app.ui.screens.home.HomeViewModel
import org.fairscan.app.ui.screens.settings.ExportFormat
import org.fairscan.app.ui.screens.settings.SettingsScreen
import org.fairscan.app.ui.screens.settings.SettingsViewModel
import org.fairscan.app.ui.theme.FairScanTheme
import org.opencv.android.OpenCVLoader
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initLibraries()

        val appContainer = (application as FairScanApp).appContainer
        val launchMode = resolveLaunchMode(intent)

        val sessionViewModel: SessionViewModel by viewModels {
            SessionViewModelFactory(
                application = application,
                launchMode = launchMode,
                appContainer = appContainer
            )
        }

        val imageRepository = sessionViewModel.imageRepository
        val viewModel: MainViewModel by viewModels {
            appContainer.viewModelFactory {
                MainViewModel(imageRepository, launchMode)
            }
        }
        val exportViewModel: ExportViewModel by viewModels {
            appContainer.viewModelFactory {
                ExportViewModel(appContainer, imageRepository)
            }
        }
        val aboutViewModel: AboutViewModel by viewModels {
            appContainer.viewModelFactory {
                AboutViewModel(appContainer, imageRepository)
            }
        }
        val homeViewModel: HomeViewModel by viewModels { appContainer.homeViewModelFactory }
        val cameraViewModel: CameraViewModel by viewModels { appContainer.cameraViewModelFactory }

        val settingsViewModel: SettingsViewModel
            by viewModels { appContainer.settingsViewModelFactory }
        lifecycleScope.launch(Dispatchers.IO) {
            exportViewModel.cleanUpOldPreparedFiles(1000 * 3600)
        }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val liveAnalysisState by cameraViewModel.liveAnalysisState.collectAsStateWithLifecycle()
            val document by viewModel.documentUiModel.collectAsStateWithLifecycle()
            val exportUiState by exportViewModel.uiState.collectAsStateWithLifecycle()
            val cameraPermission = rememberCameraPermissionState()
            CollectCameraEvents(cameraViewModel, viewModel)
            CollectExportEvents(context, exportViewModel)
            CollectAboutEvents(context, aboutViewModel, imageRepository)

            FairScanTheme {
                val navigation = navigation(viewModel, launchMode)
                val onExportClick = if (launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) {
                    {
                        lifecycleScope.launch {
                            val result = exportViewModel.generatePdfForExternalCall()
                            sendActivityResult(result)
                            viewModel.startNewDocument()
                            finish()
                        }
                        Unit
                    }
                } else {
                    navigation.toExportScreen
                }

                when (val screen = currentScreen) {
                    is Screen.Main.Home -> {
                        val recentDocs by homeViewModel.recentDocuments.collectAsStateWithLifecycle()
                        HomeScreen(
                            cameraPermission = cameraPermission,
                            currentDocument = document,
                            navigation = navigation,
                            onClearScan = { viewModel.startNewDocument() },
                            recentDocuments = recentDocs,
                            onOpenPdf = { fileUri -> openUri(fileUri, ExportFormat.PDF.mimeType) }
                        )
                    }
                    is Screen.Main.Camera -> {
                        CameraScreen(
                            viewModel,
                            cameraViewModel,
                            navigation,
                            liveAnalysisState,
                            onImageAnalyzed = { image -> cameraViewModel.liveAnalysis(image) },
                            onFinalizePressed = onExportClick,
                            cameraPermission = cameraPermission
                        )
                    }
                    is Screen.Main.Document -> {
                        DocumentScreen (
                            document = document,
                            initialPage = screen.initialPage,
                            navigation = navigation,
                            onExportClick = onExportClick,
                            onDeleteImage =  { id -> viewModel.deletePage(id) },
                            onRotateImage = { id, clockwise -> viewModel.rotateImage(id, clockwise) },
                            onPageReorder = { id, newIndex -> viewModel.movePage(id, newIndex) },
                        )
                    }
                    is Screen.Main.Export -> {
                        ExportScreenWrapper(
                            navigation = navigation,
                            uiState = exportUiState,
                            pdfActions = ExportActions(
                                initializeExportScreen = exportViewModel::initializeExportScreen,
                                setFilename = exportViewModel::setFilename,
                                share = { share(exportViewModel.applyRenaming(), exportViewModel) },
                                save = { exportViewModel.onSaveClicked() },
                                open = { item -> openUri(item.uri, item.format.mimeType) },
                            ),
                            onCloseScan = {
                                viewModel.startNewDocument()
                                viewModel.navigateTo(Screen.Main.Home)
                            }
                        )
                    }
                    is Screen.Overlay.About -> {
                        LaunchedEffect(Unit) {
                            aboutViewModel.refreshLastCapturedImageState()
                        }
                        val aboutUiState by aboutViewModel.uiState.collectAsStateWithLifecycle()
                        AboutScreen(
                            aboutUiState = aboutUiState,
                            onBack = navigation.back,
                            onCopyLogs = { aboutViewModel.onCopyLogsClicked() },
                            onContactWithLastImageClicked =
                                { aboutViewModel.onContactWithLastImageClicked() },
                            onViewLibraries = navigation.toLibrariesScreen)
                    }
                    is Screen.Overlay.Libraries -> {
                        LibrariesScreen(onBack = navigation.back)
                    }
                    is Screen.Overlay.Settings -> {
                        SettingsScreenWrapper(settingsViewModel, navigation, appContainer.logger)
                    }
                }
            }
        }
    }

    private fun resolveLaunchMode(intent: Intent?): LaunchMode {
        return when (intent?.action) {
            "org.fairscan.app.action.SCAN_TO_PDF" -> LaunchMode.EXTERNAL_SCAN_TO_PDF
            else -> LaunchMode.NORMAL
        }
    }

    @Composable
    private fun SettingsScreenWrapper(
        settingsViewModel: SettingsViewModel,
        nav: Navigation,
        logger: FileLogger,
    ) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                    settingsViewModel.setExportDirUri(uri.toString())
                } catch (e: Exception) {
                    logger.e("Settings", "Failed to set export dir to $uri", e)
                    val text = getString(R.string.error_file_picker_result)
                    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
        val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            settingsViewModel.refreshExportDirName()
        }
        SettingsScreen(
            settingsUiState,
            onChooseDirectoryClick = {
                try {
                    launcher.launch(null)
                } catch (e: Exception) {
                    val message = getString(R.string.error_file_picker_launch)
                    logger.e("Settings", message, e)
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            },
            onResetExportDirClick = { settingsViewModel.setExportDirUri(null) },
            onExportFormatChanged = { format -> settingsViewModel.setExportFormat(format) },
            onExportQualityChanged = { quality -> settingsViewModel.setExportQuality(quality) },
            onBack = nav.back,
        )
    }

    @Composable
    private fun CollectAboutEvents(
        context: Context,
        aboutViewModel: AboutViewModel,
        imageRepository: ImageRepository,
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
                    is AboutEvent.PrepareEmailWithLastImage -> {
                        val file = imageRepository.lastAddedSourceFile()
                        if (file != null) {
                            startActivity(createEmailWithImageIntent(context, file))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CollectExportEvents(
        context: Context,
        exportViewModel: ExportViewModel,
    ) {
        val storagePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                exportViewModel.onSaveClicked()
            } else {
                val message = getString(R.string.storage_permission_denied)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
        LaunchedEffect(Unit) {
            exportViewModel.events.collect { event ->
                when (event) {
                    ExportEvent.RequestSave -> {
                        checkPermissionThen(storagePermissionLauncher) {
                            exportViewModel.onRequestSave(context)
                        }
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
                    is CameraEvent.ImageCaptured -> viewModel.handleImageCaptured(event.page)
                }
            }
        }
    }

    private fun share(result: ExportResult?, viewModel: ExportViewModel) {
        if (result == null || result.files.isEmpty()) return

        viewModel.setAsShared()

        val uris = result.files.map(::uriForFile)
        val intent = Intent().apply {
            action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            type = result.format.mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        val chooser = Intent.createChooser(intent, getString(R.string.share_document))

        val resolveInfos = packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            for (uri in uris) {
                grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        startActivity(chooser)
    }

    private fun sendActivityResult(result: ExportResult?) {
        val pdf = result as? ExportResult.Pdf ?: return

        val uri = uriForFile(pdf.file)
        val resultIntent = Intent().apply {
            data = uri
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        setResult(RESULT_OK, resultIntent)
    }

    private fun uriForFile(file: File): Uri {
        return org.fairscan.app.ui.uriForFile(this, file)
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

    private fun openUri(fileUri: Uri?, mimeType: String) {
        if (fileUri == null) return
        val uriToOpen: Uri =
            if (fileUri.scheme == ContentResolver.SCHEME_CONTENT) {
                fileUri
            } else {
                uriForFile(fileUri.toFile())
            }
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriToOpen, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(openIntent, getString(R.string.open_file)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.error_no_app), Toast.LENGTH_SHORT).show()
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

    private fun navigation(viewModel: MainViewModel, launchMode: LaunchMode): Navigation = Navigation(
        toHomeScreen = { viewModel.navigateTo(Screen.Main.Home) },
        toCameraScreen = { viewModel.navigateTo(Screen.Main.Camera) },
        toDocumentScreen = { viewModel.navigateTo(Screen.Main.Document()) },
        toExportScreen = { viewModel.navigateTo(Screen.Main.Export) },
        toAboutScreen = { viewModel.navigateTo(Screen.Overlay.About) },
        toLibrariesScreen = { viewModel.navigateTo(Screen.Overlay.Libraries) },
        toSettingsScreen = if (launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) null else {
            {
                viewModel.navigateTo(Screen.Overlay.Settings)
            }
        },
        back = {
            val origin = viewModel.currentScreen.value
            viewModel.navigateBack()
            val destination = viewModel.currentScreen.value
            if (destination == origin && launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    )
}

