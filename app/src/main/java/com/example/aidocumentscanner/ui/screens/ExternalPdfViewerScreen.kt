package com.example.aidocumentscanner.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.ui.components.InAppPdfViewer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen to display external PDF files opened from file manager
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPdfViewerScreen(
    pdfUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pdfName by remember { mutableStateOf("PDF Document") }
    var pageCount by remember { mutableStateOf(0) }
    
    // Load PDF info
    LaunchedEffect(pdfUri) {
        try {
            context.contentResolver.query(pdfUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) pdfName = cursor.getString(nameIndex) ?: "PDF Document"
                }
            }
            
            // Get page count
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                pageCount = renderer.pageCount
                renderer.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            pdfName.removeSuffix(".pdf"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (pageCount > 0) {
                            Text(
                                "$pageCount pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Share button
                    IconButton(onClick = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, pdfUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to share", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    
                    // Import to app
                    IconButton(onClick = {
                        try {
                            val documentsDir = File(context.filesDir, "documents")
                            if (!documentsDir.exists()) documentsDir.mkdirs()
                            
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            val fileName = "Imported_${timestamp}.pdf"
                            val outputFile = File(documentsDir, fileName)
                            
                            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                                FileOutputStream(outputFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            Toast.makeText(context, "PDF imported!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to import", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Import")
                    }
                }
            )
        }
    ) { paddingValues ->
        // In-app PDF viewer
        InAppPdfViewer(
            pdfUri = pdfUri,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
