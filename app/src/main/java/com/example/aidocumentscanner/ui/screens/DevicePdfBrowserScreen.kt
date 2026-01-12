package com.example.aidocumentscanner.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "DevicePdfBrowser"

/**
 * Data class representing a PDF file from device storage
 */
data class DevicePdfFile(
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long,
    val dateModified: Long
)

/**
 * Screen to browse all PDF files from device storage
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePdfBrowserScreen(
    onBack: () -> Unit,
    onPdfSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    var pdfFiles by remember { mutableStateOf<List<DevicePdfFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Permission launcher for legacy storage permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result: $permissions")
        hasPermission = checkStoragePermission(context)
        if (hasPermission) {
            refreshTrigger++
        }
    }
    
    // Settings launcher for MANAGE_EXTERNAL_STORAGE
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = checkStoragePermission(context)
        Log.d(TAG, "Settings result, permission: $hasPermission")
        if (hasPermission) {
            refreshTrigger++
        }
    }
    
    // Load PDFs when permission granted or refreshed
    LaunchedEffect(hasPermission, refreshTrigger) {
        if (hasPermission) {
            isLoading = true
            Log.d(TAG, "Loading PDFs...")
            pdfFiles = withContext(Dispatchers.IO) {
                getAllPdfsFromDevice(context)
            }
            Log.d(TAG, "Found ${pdfFiles.size} PDFs")
            isLoading = false
        }
    }
    
    // Filtered list
    val filteredPdfs = remember(pdfFiles, searchQuery) {
        if (searchQuery.isBlank()) pdfFiles
        else pdfFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Device PDFs", fontWeight = FontWeight.Bold)
                        Text(
                            "${pdfFiles.size} PDFs found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        hasPermission = checkStoragePermission(context)
                        refreshTrigger++ 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search PDFs...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            if (!hasPermission) {
                // Permission required
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Storage Access Required",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "To browse PDF files, please grant storage access",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    // Android 11+ needs special all files access
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        settingsLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        settingsLauncher.launch(intent)
                                    }
                                } else {
                                    // Legacy permission request
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Access")
                        }
                    }
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scanning for PDFs...")
                    }
                }
            } else if (filteredPdfs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "No PDFs match your search"
                            else "No PDFs found",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPdfs) { pdf ->
                        DevicePdfCard(
                            pdf = pdf,
                            onClick = { onPdfSelected(pdf.uri) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Check if storage permission is granted
 */
private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11+ - check MANAGE_EXTERNAL_STORAGE
        Environment.isExternalStorageManager()
    } else {
        // Below Android 11 - check READ_EXTERNAL_STORAGE
        ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun DevicePdfCard(
    pdf: DevicePdfFile,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.1f)
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = Color(0xFFEF4444)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pdf.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatFileSize(pdf.size)} • ${formatDate(pdf.dateModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Get all PDF files from device storage
 */
private fun getAllPdfsFromDevice(context: Context): List<DevicePdfFile> {
    val pdfList = mutableListOf<DevicePdfFile>()
    
    // Method 1: MediaStore query
    try {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )
        
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("application/pdf", "%.pdf")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            
            Log.d(TAG, "MediaStore returned ${cursor.count} results")
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val size = cursor.getLong(sizeColumn)
                val dateModified = cursor.getLong(dateColumn) * 1000
                val path = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""
                
                if (name.endsWith(".pdf", ignoreCase = true)) {
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"), id
                    )
                    pdfList.add(DevicePdfFile(uri, path, name, size, dateModified))
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "MediaStore query error: ${e.message}")
    }
    
    // Method 2: Direct file scanning
    val directories = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        File(Environment.getExternalStorageDirectory(), "Download"),
        File(Environment.getExternalStorageDirectory(), "Documents"),
        File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Documents"),
        File(Environment.getExternalStorageDirectory(), "Telegram/Telegram Documents"),
        Environment.getExternalStorageDirectory()
    )
    
    for (dir in directories) {
        try {
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                Log.d(TAG, "Scanning: ${dir.absolutePath}")
                scanDirectoryForPdfs(dir, pdfList, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan error for ${dir.absolutePath}: ${e.message}")
        }
    }
    
    Log.d(TAG, "Total PDFs: ${pdfList.size}")
    return pdfList.distinctBy { it.name + it.size }.sortedByDescending { it.dateModified }
}

private fun scanDirectoryForPdfs(dir: File, pdfList: MutableList<DevicePdfFile>, depth: Int) {
    if (depth > 4) return
    
    try {
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory && file.canRead() && !file.name.startsWith(".") -> {
                    scanDirectoryForPdfs(file, pdfList, depth + 1)
                }
                file.isFile && file.extension.equals("pdf", ignoreCase = true) -> {
                    val exists = pdfList.any { it.name == file.name && it.size == file.length() }
                    if (!exists) {
                        pdfList.add(DevicePdfFile(
                            uri = Uri.fromFile(file),
                            path = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            dateModified = file.lastModified()
                        ))
                    }
                }
            }
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "Security error: ${e.message}")
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
