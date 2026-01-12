package com.example.aidocumentscanner.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.pdf.PdfEditor
import com.example.aidocumentscanner.pdf.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class QualityLevel(
    val label: String,
    val description: String,
    val dpi: Int,
    val jpegQuality: Int,
    val sizeReduction: String
) {
    LOW("Low", "Maximum compression", 72, 50, "~70% smaller"),
    BALANCED("Balanced", "Good quality & size", 150, 75, "~40% smaller"),
    HIGH("High", "Best quality", 300, 90, "~20% smaller"),
    WHATSAPP("WhatsApp Ready", "Optimized for sharing", 100, 60, "<2 MB guaranteed")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOptimizerScreen(
    onBack: () -> Unit,
    onOptimized: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DocumentRepository(context) }
    val documents by repository.getAllDocuments().collectAsState(initial = emptyList())
    
    var selectedDocument by remember { mutableStateOf<Document?>(null) }
    var selectedQuality by remember { mutableStateOf(QualityLevel.BALANCED) }
    var isProcessing by remember { mutableStateOf(false) }
    var estimatedSize by remember { mutableStateOf<Long?>(null) }
    
    // Calculate estimated size when quality changes
    LaunchedEffect(selectedDocument, selectedQuality) {
        selectedDocument?.let { doc ->
            val originalSize = doc.size
            val reductionFactor = when (selectedQuality) {
                QualityLevel.LOW -> 0.3f
                QualityLevel.BALANCED -> 0.6f
                QualityLevel.HIGH -> 0.8f
                QualityLevel.WHATSAPP -> 0.25f
            }
            estimatedSize = (originalSize * reductionFactor).toLong()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PDF Optimizer", fontWeight = FontWeight.Bold)
                        Text(
                            "Reduce file size for easy sharing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedDocument != null) {
                            selectedDocument = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (selectedDocument == null) {
            // Document selection
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No documents to optimize", fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Select a PDF to optimize",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    items(documents) { doc ->
                        Card(
                            onClick = { selectedDocument = doc },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        doc.name,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${doc.pageCount} pages • ${PdfGenerator.formatFileSize(doc.size)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        } else {
            // Optimization options
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Document info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                selectedDocument!!.name,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Original: ${PdfGenerator.formatFileSize(selectedDocument!!.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        estimatedSize?.let { size ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text("→", color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "~${PdfGenerator.formatFileSize(size)}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                Text(
                    "Select Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Quality options
                QualityLevel.entries.forEach { quality ->
                    val isSelected = selectedQuality == quality
                    Card(
                        onClick = { selectedQuality = quality },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = if (isSelected)
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedQuality = quality }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(quality.label, fontWeight = FontWeight.Medium)
                                Text(
                                    quality.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (quality == QualityLevel.WHATSAPP)
                                    Color(0xFF25D366).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    quality.sizeReduction,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (quality == QualityLevel.WHATSAPP)
                                        Color(0xFF25D366) 
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Optimize button
                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                PdfEditor.optimizePdf(
                                    context,
                                    selectedDocument!!.pdfPath,
                                    selectedQuality.jpegQuality
                                )
                            }
                            
                            result.onSuccess { newPath ->
                                val newDoc = Document(
                                    name = "${selectedDocument!!.name}_optimized",
                                    pdfPath = newPath,
                                    thumbnailPath = selectedDocument!!.thumbnailPath,
                                    pageCount = selectedDocument!!.pageCount,
                                    size = File(newPath).length()
                                )
                                val docId = repository.insertDocument(newDoc)
                                Toast.makeText(context, "PDF optimized successfully!", Toast.LENGTH_SHORT).show()
                                onOptimized(docId)
                            }
                            
                            result.onFailure { error ->
                                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                            
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Optimizing...")
                    } else {
                        Icon(Icons.Default.Compress, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Optimize PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
