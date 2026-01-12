package com.example.aidocumentscanner.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "PdfViewer"

/**
 * In-app PDF viewer that renders PDF pages
 */
@Composable
fun InAppPdfViewer(
    pdfUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    
    // Global zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Load PDF pages
    LaunchedEffect(pdfUri) {
        isLoading = true
        error = null
        try {
            pages = withContext(Dispatchers.IO) {
                renderPdfPages(context, pdfUri)
            }
            if (pages.isEmpty()) {
                error = "Could not render PDF"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load PDF: ${e.message}", e)
            error = e.message ?: "Failed to load PDF"
        }
        isLoading = false
    }
    
    // Track current page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { currentPage = it }
    }
    
    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading PDF...")
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Failed to load PDF",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            pages.isNotEmpty() -> {
                // Global zoom/pan container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                do {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val fingers = event.changes.size
                                    
                                    var shouldConsume = false
                                    
                                    if (fingers > 1) {
                                        // Multi-touch: Zooming
                                        shouldConsume = true
                                        scale = (scale * zoom).coerceIn(1f, 4f)
                                        
                                        if (scale > 1f) {
                                            val maxX = (containerSize.width * (scale - 1)) / 2f
                                            val maxY = (containerSize.height * (scale - 1)) / 2f
                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    } else if (scale > 1f) {
                                        // Single touch while zoomed: Panning
                                        shouldConsume = true
                                        val maxX = (containerSize.width * (scale - 1)) / 2f
                                        val maxY = (containerSize.height * (scale - 1)) / 2f
                                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                    }
                                    // Single touch at scale 1f: Allow list scroll
                                    
                                    if (shouldConsume) {
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) {
                                                change.consume()
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                ) {
                    // PDF pages list - continuous, no gaps, white background
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        userScrollEnabled = scale <= 1f
                    ) {
                        itemsIndexed(pages) { index, bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
                
                // Page indicator
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Page ${currentPage + 1} of ${pages.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (scale > 1f) {
                            Text(
                                text = "• ${(scale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// PdfPageCard removed - pages now rendered inline for seamless viewing

/**
 * Render all pages of a PDF to bitmaps
 */
private fun renderPdfPages(context: Context, pdfUri: Uri): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    
    try {
        // For content:// URIs, we need to copy to a temp file first
        val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
        
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        
        val displayMetrics = context.resources.displayMetrics
        // Render at higher resolution for better zoom quality (1.5x screen width)
        val renderWidth = (displayMetrics.widthPixels * 1.5f).toInt()
        
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            
            // Calculate scaled dimensions maintaining aspect ratio
            val scale = renderWidth.toFloat() / page.width
            val height = (page.height * scale).toInt()
            
            val bitmap = Bitmap.createBitmap(renderWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            
            pages.add(bitmap)
        }
        
        renderer.close()
        pfd.close()
        
        // Clean up temp file
        tempFile.delete()
        
    } catch (e: Exception) {
        Log.e(TAG, "Error rendering PDF: ${e.message}", e)
        throw e
    }
    
    return pages
}

/**
 * Render PDF pages from a file path
 */
fun renderPdfPagesFromPath(context: Context, pdfPath: String): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    
    try {
        val file = File(pdfPath)
        if (!file.exists()) {
            Log.e(TAG, "PDF file not found: $pdfPath")
            return pages
        }
        
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        
        val displayMetrics = context.resources.displayMetrics
        // Render at higher resolution (1.5x)
        val renderWidth = (displayMetrics.widthPixels * 1.5f).toInt()
        
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            
            val scale = renderWidth.toFloat() / page.width
            val height = (page.height * scale).toInt()
            
            val bitmap = Bitmap.createBitmap(renderWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            
            pages.add(bitmap)
        }
        
        renderer.close()
        pfd.close()
        
    } catch (e: Exception) {
        Log.e(TAG, "Error rendering PDF from path: ${e.message}", e)
    }
    
    return pages
}
