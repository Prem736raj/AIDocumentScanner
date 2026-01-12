package com.example.aidocumentscanner.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.aidocumentscanner.ui.screens.SettingsPreferences
import com.example.aidocumentscanner.ui.screens.StorageLocation
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.text.pdf.PdfCopy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * PDF Generator using iTextPDF library.
 * All processing is done locally - no internet required.
 * PDFs are saved based on user's storage preference.
 */
object PdfGenerator {
    
    private const val TAG = "PdfGenerator"
    private const val APP_FOLDER = "AIDocumentScanner"
    
    enum class PageSizeType(val rectangle: Rectangle) {
        A4(PageSize.A4),
        LETTER(PageSize.LETTER),
        LEGAL(PageSize.LEGAL),
        FIT_IMAGE(PageSize.A4)
    }
    
    enum class QualityType(val jpegQuality: Int, val maxDimension: Int, val label: String) {
        STANDARD(80, 1920, "Standard (1080p)"),
        HIGH(90, 2560, "High (2K)"),
        ULTRA(100, 3840, "Ultra (4K)")
    }
    
    /**
     * Generate PDF from list of bitmaps.
     * Saves based on user's storage preference.
     */
    fun generatePdf(
        context: Context,
        images: List<Bitmap>,
        fileName: String,
        pageSize: PageSizeType = PageSizeType.A4,
        quality: QualityType = QualityType.HIGH
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val pdfFileName = "${safeFileName}_$timestamp.pdf"
        
        // Always create internal copy first (for app's document list)
        val documentsDir = File(context.filesDir, "documents")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
        val internalFile = File(documentsDir, pdfFileName)
        
        // Generate PDF to internal storage
        generatePdfToStream(FileOutputStream(internalFile), images, pageSize, quality)
        
        // Also save to user's preferred location if not internal
        val storageLocation = SettingsPreferences.getStorageLocation(context)
        if (storageLocation != StorageLocation.INTERNAL) {
            try {
                saveToPublicStorage(context, internalFile, pdfFileName, storageLocation)
                Log.d(TAG, "PDF saved to ${storageLocation.label}: $pdfFileName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to ${storageLocation.label}: ${e.message}", e)
            }
        }
        
        return internalFile.absolutePath
    }
    
    /**
     * Generate PDF to an OutputStream
     */
    private fun generatePdfToStream(
        outputStream: OutputStream,
        images: List<Bitmap>,
        pageSize: PageSizeType,
        quality: QualityType
    ) {
        val document = Document()
        
        try {
            PdfWriter.getInstance(document, outputStream)
            document.open()
            
            for ((index, bitmap) in images.withIndex()) {
                val scaledBitmap = scaleForQuality(bitmap, quality)
                
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, stream)
                val imageData = stream.toByteArray()
                stream.close()
                
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                
                val image = Image.getInstance(imageData)
                
                if (pageSize == PageSizeType.FIT_IMAGE) {
                    val pageRect = Rectangle(image.width, image.height)
                    document.setPageSize(pageRect)
                    if (index > 0) document.newPage()
                    image.setAbsolutePosition(0f, 0f)
                } else {
                    if (index > 0) document.newPage()
                    
                    val pageWidth = pageSize.rectangle.width - 40
                    val pageHeight = pageSize.rectangle.height - 40
                    
                    image.scaleToFit(pageWidth, pageHeight)
                    
                    val x = (pageSize.rectangle.width - image.scaledWidth) / 2
                    val y = (pageSize.rectangle.height - image.scaledHeight) / 2
                    image.setAbsolutePosition(x, y)
                }
                
                document.add(image)
            }
        } finally {
            document.close()
            outputStream.close()
        }
    }
    
    /**
     * Save PDF file to user's preferred public storage location
     */
    private fun saveToPublicStorage(
        context: Context, 
        sourceFile: File, 
        fileName: String,
        location: StorageLocation
    ) {
        val relativePath = when (location) {
            StorageLocation.DOCUMENTS -> Environment.DIRECTORY_DOCUMENTS + "/$APP_FOLDER"
            StorageLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS + "/$APP_FOLDER"
            StorageLocation.INTERNAL -> return // No public storage needed
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ use MediaStore
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        } else {
            // Android 9 and below - direct file access
            val baseDir = when (location) {
                StorageLocation.DOCUMENTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                StorageLocation.DOWNLOADS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                StorageLocation.INTERNAL -> return
            }
            
            val destDir = File(baseDir, APP_FOLDER)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            
            val destFile = File(destDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
        }
    }
    
    /**
     * Scale bitmap for quality setting
     */
    private fun scaleForQuality(bitmap: Bitmap, quality: QualityType): Bitmap {
        val maxDim = quality.maxDimension
        val currentMax = maxOf(bitmap.width, bitmap.height)
        
        if (currentMax <= maxDim) {
            return bitmap
        }
        
        val scale = maxDim.toFloat() / currentMax
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Generate thumbnail from bitmap
     */
    fun generateThumbnail(context: Context, bitmap: Bitmap, documentId: String): String {
        val thumbnailsDir = File(context.filesDir, "thumbnails")
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }
        
        val maxSize = 400
        val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
        val thumbWidth = (bitmap.width * scale).toInt()
        val thumbHeight = (bitmap.height * scale).toInt()
        
        val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
        
        val thumbFile = File(thumbnailsDir, "thumb_$documentId.jpg")
        FileOutputStream(thumbFile).use { fos ->
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        }
        
        return thumbFile.absolutePath
    }
    
    /**
     * Get file size of PDF
     */
    fun getFileSize(filePath: String): Long {
        return File(filePath).length()
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
    
    /**
     * Delete PDF and associated files
     */
    fun deleteDocument(pdfPath: String, thumbnailPath: String?) {
        File(pdfPath).delete()
        thumbnailPath?.let { File(it).delete() }
    }
    
    /**
     * Get page count from PDF
     */
    fun getPageCount(pdfPath: String): Int {
        return try {
            val reader = PdfReader(pdfPath)
            val count = reader.numberOfPages
            reader.close()
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PDF: ${e.message}")
            0
        }
    }
}
