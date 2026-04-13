package com.example.aidocumentscanner.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.aidocumentscanner.ui.screens.*
import com.example.aidocumentscanner.ui.theme.ThemeMode

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Editor : Screen("editor/{imageUri}") {
        fun createRoute(imageUri: String) = "editor/$imageUri"
    }
    object PdfPreview : Screen("pdf_preview")
    object Documents : Screen("documents")
    object Settings : Screen("settings")
    object PdfViewer : Screen("pdf_viewer/{documentId}?page={page}") {
        fun createRoute(documentId: Long, page: Int = 0) = "pdf_viewer/$documentId?page=$page"
    }
    object PdfTools : Screen("pdf_tools")
    object Search : Screen("search")
    object PdfOptimizer : Screen("pdf_optimizer")
    object DevicePdfs : Screen("device_pdfs")
    object ExternalPdfViewer : Screen("external_pdf_viewer")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    pages: MutableList<android.graphics.Bitmap>,
    onAddPage: (android.graphics.Bitmap) -> Unit,
    onAddPages: (List<android.graphics.Bitmap>) -> Unit,
    onClearPages: () -> Unit,
    onRemovePage: (Int) -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    externalPdfUri: Uri? = null,
    onExternalPdfHandled: () -> Unit = {}
) {
    // Shared state for PDF URI to view
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    
    // Handle external PDF when app is opened with a PDF file
    LaunchedEffect(externalPdfUri) {
        externalPdfUri?.let { uri ->
            selectedPdfUri = uri
            navController.navigate(Screen.ExternalPdfViewer.route)
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onScanClick = { navController.navigate(Screen.Camera.route) },
                onDocumentsClick = { navController.navigate(Screen.Documents.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onPdfToolsClick = { navController.navigate(Screen.PdfTools.route) },
                onDocumentClick = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId))
                },
                onImagesSelected = { bitmaps ->
                    bitmaps.forEach { onAddPage(it) }
                    if (bitmaps.isNotEmpty()) {
                        navController.navigate(Screen.Editor.createRoute("imported")) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }
                },
                onDevicePdfsClick = { navController.navigate(Screen.DevicePdfs.route) },
                onOptimizeClick = { navController.navigate(Screen.PdfOptimizer.route) }
            )
        }
        
        composable(Screen.Camera.route) {
            CameraScreen(
                onImageCaptured = { bitmap ->
                    onAddPage(bitmap)
                    navController.navigate(Screen.Editor.createRoute("captured")) {
                        popUpTo(Screen.Camera.route) { inclusive = true }
                    }
                },
                onMultipleImagesCaptured = { bitmaps ->
                    onAddPages(bitmaps)
                    navController.navigate(Screen.Editor.createRoute("captured")) {
                        popUpTo(Screen.Camera.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.Editor.route,
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType })
        ) {
            EditorScreen(
                pages = pages,
                onContinue = { editedPages ->
                    onClearPages()
                    onAddPages(editedPages)
                    navController.navigate(Screen.PdfPreview.route) {
                        popUpTo(Screen.Editor.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                onAddMorePages = { navController.navigate(Screen.Camera.route) },
                onRemovePage = onRemovePage
            )
        }
        
        composable(Screen.PdfPreview.route) {
            PdfPreviewScreen(
                pages = pages,
                onSave = { documentId ->
                    onClearPages()
                    navController.navigate(Screen.PdfViewer.createRoute(documentId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onAddMore = {
                    navController.navigate(Screen.Camera.route)
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Documents.route) {
            DocumentsScreen(
                onDocumentClick = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId))
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                currentThemeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
        
        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.LongType },
                navArgument("page") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: 0L
            val initialPage = backStackEntry.arguments?.getInt("page") ?: 0
            PdfViewerScreen(
                documentId = documentId,
                initialPage = initialPage,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.PdfTools.route) {
            PdfToolsScreen(
                onBack = { navController.popBackStack() },
                onMergeComplete = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId))
                }
            )
        }
        
        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onResultClick = { documentId, pageNum ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId, pageNum))
                }
            )
        }
        
        composable(Screen.PdfOptimizer.route) {
            PdfOptimizerScreen(
                onBack = { navController.popBackStack() },
                onOptimized = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId)) {
                        popUpTo(Screen.PdfOptimizer.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.DevicePdfs.route) {
            DevicePdfBrowserScreen(
                onBack = { navController.popBackStack() },
                onPdfSelected = { uri ->
                    selectedPdfUri = uri
                    navController.navigate(Screen.ExternalPdfViewer.route)
                }
            )
        }
        
        composable(Screen.ExternalPdfViewer.route) {
            selectedPdfUri?.let { uri ->
                ExternalPdfViewerScreen(
                    pdfUri = uri,
                    onBack = { 
                        onExternalPdfHandled()
                        navController.popBackStack()
                    }
                )
            } ?: run {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}
