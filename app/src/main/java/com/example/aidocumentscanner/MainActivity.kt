package com.example.aidocumentscanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.example.aidocumentscanner.navigation.AppNavigation
import com.example.aidocumentscanner.ui.theme.AIDocumentScannerTheme
import com.example.aidocumentscanner.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    
    private val externalPdfUriState = MutableStateFlow<Uri?>(null)

    companion object {
        private const val TAG = "AIDocumentScanner"
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenCV
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV loaded successfully")
        } else {
            Log.e(TAG, "OpenCV initialization failed")
        }
        
        enableEdgeToEdge()
        
        // Check if app was opened with a PDF file
        externalPdfUriState.value = handlePdfIntent(intent)
        
        // Load saved theme preference
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedThemeMode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        
        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.valueOf(savedThemeMode)) }
            val externalPdfUri by externalPdfUriState.collectAsState()
            AIDocumentScannerTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                
                // Shared state for scanned pages
                val pages = remember { mutableStateListOf<Bitmap>() }
                
                AppNavigation(
                    navController = navController,
                    pages = pages,
                    onAddPage = { bitmap -> pages.add(bitmap) },
                    onAddPages = { bitmaps -> pages.addAll(bitmaps) },
                    onClearPages = { pages.clear() },
                    onRemovePage = { index -> 
                        if (index in pages.indices) {
                            pages.removeAt(index)
                        }
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        themeMode = newMode
                        // Save theme preference
                        prefs.edit().putString(KEY_THEME_MODE, newMode.name).apply()
                    },
                    externalPdfUri = externalPdfUri,
                    onExternalPdfHandled = { externalPdfUriState.value = null }
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep Activity intent in sync so future getIntent() reads the latest deep-link/share intent.
        setIntent(intent)
        // Handle new PDF intent when app is already running
        handlePdfIntent(intent)?.let { uri ->
            Log.d(TAG, "Received new PDF intent: $uri")
            externalPdfUriState.value = uri
        }
    }
    
    private fun handlePdfIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        
        val action = intent.action
        val type = intent.type
        
        Log.d(TAG, "Intent action: $action, type: $type")
        
        if (action == Intent.ACTION_VIEW && type == "application/pdf") {
            return intent.data
        }
        
        return null
    }
    
}
