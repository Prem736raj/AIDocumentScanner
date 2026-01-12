package com.example.aidocumentscanner.export

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Document exporter for various formats.
 * Exports images to different document formats.
 */
object DocumentExporter {
    
    private const val TAG = "DocumentExporter"
    
    enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
        PDF("pdf", "application/pdf", "PDF Document"),
        // Note: For full PPT/Word/Excel support, Apache POI library is needed (~20MB)
        // These are simplified image-based exports
        IMAGES_ZIP("zip", "application/zip", "Images (ZIP)"),
        SINGLE_IMAGE("jpg", "image/jpeg", "Single Image (JPG)"),
        PNG_IMAGES("png", "image/png", "Images (PNG)")
    }
    
    /**
     * Export pages to specified format
     */
    fun export(
        context: Context,
        images: List<Bitmap>,
        fileName: String,
        format: ExportFormat,
        quality: Int = 90
    ): Result<String> {
        return try {
            val outputPath = when (format) {
                ExportFormat.PDF -> {
                    // Use PdfGenerator for PDF
                    throw UnsupportedOperationException("Use PdfGenerator for PDF export")
                }
                ExportFormat.IMAGES_ZIP -> {
                    exportAsZip(context, images, fileName, quality)
                }
                ExportFormat.SINGLE_IMAGE -> {
                    exportAsSingleImage(context, images, fileName, quality)
                }
                ExportFormat.PNG_IMAGES -> {
                    exportAsPng(context, images, fileName)
                }
            }
            Result.success(outputPath)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Export images as ZIP archive
     */
    private fun exportAsZip(
        context: Context,
        images: List<Bitmap>,
        fileName: String,
        quality: Int
    ): String {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val zipFile = File(exportDir, "${safeFileName}_$timestamp.zip")
        
        java.util.zip.ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            images.forEachIndexed { index, bitmap ->
                val entryName = "page_${index + 1}.jpg"
                zip.putNextEntry(java.util.zip.ZipEntry(entryName))
                
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                zip.write(stream.toByteArray())
                
                zip.closeEntry()
            }
        }
        
        return zipFile.absolutePath
    }
    
    /**
     * Export as single combined image (vertical stack)
     */
    private fun exportAsSingleImage(
        context: Context,
        images: List<Bitmap>,
        fileName: String,
        quality: Int
    ): String {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outputFile = File(exportDir, "${safeFileName}_$timestamp.jpg")
        
        if (images.size == 1) {
            // Single image, just save it
            FileOutputStream(outputFile).use { fos ->
                images[0].compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
        } else {
            // Combine images vertically
            val maxWidth = images.maxOf { it.width }
            val totalHeight = images.sumOf { it.height }
            
            val combined = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(combined)
            
            var currentY = 0f
            images.forEach { bitmap ->
                canvas.drawBitmap(bitmap, 0f, currentY, null)
                currentY += bitmap.height
            }
            
            FileOutputStream(outputFile).use { fos ->
                combined.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            
            combined.recycle()
        }
        
        return outputFile.absolutePath
    }
    
    /**
     * Export as PNG images in a folder
     */
    private fun exportAsPng(
        context: Context,
        images: List<Bitmap>,
        fileName: String
    ): String {
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val exportDir = File(context.filesDir, "exports/${safeFileName}_$timestamp")
        if (!exportDir.exists()) exportDir.mkdirs()
        
        images.forEachIndexed { index, bitmap ->
            val pageFile = File(exportDir, "page_${index + 1}.png")
            FileOutputStream(pageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        }
        
        return exportDir.absolutePath
    }
    
    /**
     * Get export directory
     */
    fun getExportDirectory(context: Context): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Delete exported file
     */
    fun deleteExport(filePath: String) {
        val file = File(filePath)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }
}
