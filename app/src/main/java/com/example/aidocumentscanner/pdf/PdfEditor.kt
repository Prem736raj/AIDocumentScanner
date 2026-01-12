package com.example.aidocumentscanner.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.aidocumentscanner.ui.screens.SettingsPreferences
import com.example.aidocumentscanner.ui.screens.StorageLocation
import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfCopy
import com.itextpdf.text.pdf.PdfReader
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * PDF Editor utility class for merge, split, and page manipulation.
 * All operations done offline using iTextPDF.
 * Saves to user's preferred storage location.
 */
object PdfEditor {
    
    private const val TAG = "PdfEditor"
    private const val APP_FOLDER = "AIDocumentScanner"
    
    /**
     * Merge multiple PDFs into one
     */
    fun mergePdfs(
        context: Context,
        pdfPaths: List<String>,
        outputName: String
    ): Result<String> {
        return try {
            if (pdfPaths.isEmpty()) {
                return Result.failure(Exception("No PDFs to merge"))
            }
            
            if (pdfPaths.size == 1) {
                return Result.success(pdfPaths[0])
            }
            
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) documentsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safeFileName = outputName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val pdfFileName = "${safeFileName}_merged_$timestamp.pdf"
            val outputFile = File(documentsDir, pdfFileName)
            
            val document = Document()
            val copy = PdfCopy(document, FileOutputStream(outputFile))
            
            document.open()
            
            for (pdfPath in pdfPaths) {
                val reader = PdfReader(pdfPath)
                for (page in 1..reader.numberOfPages) {
                    copy.addPage(copy.getImportedPage(reader, page))
                }
                reader.close()
            }
            
            document.close()
            
            // Save to public storage based on preference
            saveToPublicStorage(context, outputFile, pdfFileName)
            
            Log.d(TAG, "Merged ${pdfPaths.size} PDFs into ${outputFile.absolutePath}")
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Merge failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Split PDF into separate files
     */
    fun splitPdf(
        context: Context,
        pdfPath: String,
        pageRanges: List<IntRange>
    ): Result<List<String>> {
        return try {
            val reader = PdfReader(pdfPath)
            val totalPages = reader.numberOfPages
            
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) documentsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseName = File(pdfPath).nameWithoutExtension
            
            val outputPaths = mutableListOf<String>()
            
            for ((index, range) in pageRanges.withIndex()) {
                val pdfFileName = "${baseName}_part${index + 1}_$timestamp.pdf"
                val outputFile = File(documentsDir, pdfFileName)
                
                val document = Document()
                val copy = PdfCopy(document, FileOutputStream(outputFile))
                document.open()
                
                for (page in range) {
                    if (page in 1..totalPages) {
                        copy.addPage(copy.getImportedPage(reader, page))
                    }
                }
                
                document.close()
                
                // Save to public storage
                saveToPublicStorage(context, outputFile, pdfFileName)
                
                outputPaths.add(outputFile.absolutePath)
            }
            
            reader.close()
            
            Log.d(TAG, "Split PDF into ${outputPaths.size} parts")
            Result.success(outputPaths)
        } catch (e: Exception) {
            Log.e(TAG, "Split failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove specific pages from PDF
     */
    fun removePages(
        context: Context,
        pdfPath: String,
        pagesToRemove: List<Int>
    ): Result<String> {
        return try {
            val reader = PdfReader(pdfPath)
            val totalPages = reader.numberOfPages
            
            val pagesToKeep = (1..totalPages).filter { it !in pagesToRemove }
            
            if (pagesToKeep.isEmpty()) {
                reader.close()
                return Result.failure(Exception("Cannot remove all pages"))
            }
            
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) documentsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseName = File(pdfPath).nameWithoutExtension
            val pdfFileName = "${baseName}_edited_$timestamp.pdf"
            val outputFile = File(documentsDir, pdfFileName)
            
            val document = Document()
            val copy = PdfCopy(document, FileOutputStream(outputFile))
            document.open()
            
            for (page in pagesToKeep) {
                copy.addPage(copy.getImportedPage(reader, page))
            }
            
            document.close()
            reader.close()
            
            // Save to public storage
            saveToPublicStorage(context, outputFile, pdfFileName)
            
            Log.d(TAG, "Removed ${pagesToRemove.size} pages, kept ${pagesToKeep.size} pages")
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Remove pages failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Extract specific pages from PDF as images (JPEG)
     * Saves images to gallery/Pictures folder
     */
    fun extractPagesAsImages(
        context: Context,
        pdfPath: String,
        pagesToExtract: List<Int>
    ): Result<Int> {
        return try {
            val file = File(pdfPath)
            val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            val totalPages = pdfRenderer.pageCount
            
            val validPages = pagesToExtract.filter { it in 1..totalPages }
            
            if (validPages.isEmpty()) {
                pdfRenderer.close()
                parcelFileDescriptor.close()
                return Result.failure(Exception("No valid pages to extract"))
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseName = File(pdfPath).nameWithoutExtension
            var savedCount = 0
            
            for (pageNum in validPages) {
                val pageIndex = pageNum - 1
                val page = pdfRenderer.openPage(pageIndex)
                
                // Render at high quality
                val scale = 3f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Fill with white background
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                
                // Save to gallery
                val imageFileName = "${baseName}_page${pageNum}_$timestamp.jpg"
                saveImageToGallery(context, bitmap, imageFileName)
                savedCount++
                
                bitmap.recycle()
            }
            
            pdfRenderer.close()
            parcelFileDescriptor.close()
            
            Log.d(TAG, "Extracted $savedCount pages as images")
            Result.success(savedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Extract pages as images failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Split PDF by extracting specific pages into a new PDF
     */
    fun splitPdfByPages(
        context: Context,
        pdfPath: String,
        pagesToExtract: List<Int>
    ): Result<String> {
        return try {
            val reader = PdfReader(pdfPath)
            val totalPages = reader.numberOfPages
            
            val validPages = pagesToExtract.filter { it in 1..totalPages }
            
            if (validPages.isEmpty()) {
                reader.close()
                return Result.failure(Exception("No valid pages to extract"))
            }
            
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) documentsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseName = File(pdfPath).nameWithoutExtension
            val pdfFileName = "${baseName}_split_$timestamp.pdf"
            val outputFile = File(documentsDir, pdfFileName)
            
            val document = Document()
            val copy = PdfCopy(document, FileOutputStream(outputFile))
            document.open()
            
            for (page in validPages) {
                copy.addPage(copy.getImportedPage(reader, page))
            }
            
            document.close()
            reader.close()
            
            // Save to public storage
            saveToPublicStorage(context, outputFile, pdfFileName)
            
            Log.d(TAG, "Split PDF with ${validPages.size} pages")
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Split PDF failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save image bitmap to gallery/Pictures folder
     */
    private fun saveImageToGallery(context: Context, bitmap: Bitmap, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ use MediaStore
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/$APP_FOLDER")
            }
            
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
            }
            Log.d(TAG, "Saved image to gallery: $fileName")
        } else {
            // Android 9 and below
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                APP_FOLDER
            )
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            
            val imageFile = File(picturesDir, fileName)
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            Log.d(TAG, "Saved image to: ${imageFile.absolutePath}")
        }
    }
    
    /**
     * Save PDF to user's preferred public storage location
     */
    private fun saveToPublicStorage(context: Context, sourceFile: File, fileName: String) {
        try {
            val storageLocation = SettingsPreferences.getStorageLocation(context)
            
            if (storageLocation == StorageLocation.INTERNAL) {
                return // No public storage needed
            }
            
            val relativePath = when (storageLocation) {
                StorageLocation.DOCUMENTS -> Environment.DIRECTORY_DOCUMENTS + "/$APP_FOLDER"
                StorageLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS + "/$APP_FOLDER"
                StorageLocation.INTERNAL -> return
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
                Log.d(TAG, "Saved to public storage: $fileName")
            } else {
                // Android 9 and below - direct file access
                val baseDir = when (storageLocation) {
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
                Log.d(TAG, "Saved to public storage: ${destFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to public storage: ${e.message}", e)
        }
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
            Log.e(TAG, "Failed to get page count: ${e.message}")
            0
        }
    }
    
    /**
     * Render PDF page to bitmap for preview
     */
    fun renderPageToBitmap(context: Context, pdfPath: String, pageIndex: Int): Bitmap? {
        return try {
            val file = File(pdfPath)
            val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            
            if (pageIndex < 0 || pageIndex >= pdfRenderer.pageCount) {
                pdfRenderer.close()
                parcelFileDescriptor.close()
                return null
            }
            
            val page = pdfRenderer.openPage(pageIndex)
            
            val scale = 2f
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            page.close()
            pdfRenderer.close()
            parcelFileDescriptor.close()
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render page: ${e.message}")
            null
        }
    }
    
    /**
     * Render all pages of PDF to bitmaps
     */
    fun renderAllPagesToBitmap(context: Context, pdfPath: String): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        
        try {
            val file = File(pdfPath)
            val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            
            for (i in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(i)
                
                val scale = 1.5f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                page.close()
                bitmaps.add(bitmap)
            }
            
            pdfRenderer.close()
            parcelFileDescriptor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render pages: ${e.message}")
        }
        
        return bitmaps
    }
    
    /**
     * Add watermark text to all pages of PDF
     */
    fun addWatermark(
        context: Context,
        pdfPath: String,
        watermarkText: String
    ): Result<String> {
        return try {
            val reader = PdfReader(pdfPath)
            
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) documentsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseName = File(pdfPath).nameWithoutExtension
            val pdfFileName = "${baseName}_watermarked_$timestamp.pdf"
            val outputFile = File(documentsDir, pdfFileName)
            
            val stamper = com.itextpdf.text.pdf.PdfStamper(reader, FileOutputStream(outputFile))
            
            val font = com.itextpdf.text.pdf.BaseFont.createFont(
                com.itextpdf.text.pdf.BaseFont.HELVETICA,
                com.itextpdf.text.pdf.BaseFont.WINANSI,
                com.itextpdf.text.pdf.BaseFont.EMBEDDED
            )
            
            for (i in 1..reader.numberOfPages) {
                val pageSize = reader.getPageSize(i)
                val canvas = stamper.getOverContent(i)
                
                canvas.saveState()
                val gs = com.itextpdf.text.pdf.PdfGState()
                gs.setFillOpacity(0.3f)
                gs.setStrokeOpacity(0.3f)
                canvas.setGState(gs)
                canvas.beginText()
                canvas.setFontAndSize(font, 60f)
                canvas.setColorFill(com.itextpdf.text.BaseColor.GRAY)
                canvas.showTextAligned(
                    com.itextpdf.text.Element.ALIGN_CENTER,
                    watermarkText,
                    pageSize.width / 2,
                    pageSize.height / 2,
                    45f
                )
                canvas.endText()
                canvas.restoreState()
            }
            
            stamper.close()
            reader.close()
            
            // Save to public storage
            saveToPublicStorage(context, outputFile, pdfFileName)
            
            Log.d(TAG, "Added watermark to PDF: $pdfFileName")
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add watermark: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Password protect a PDF
     */
    fun passwordProtect(
        context: Context,
        pdfPath: String,
        password: String
    ): Result<String> {
        return try {
            val reader = PdfReader(pdfPath)
            
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) documentsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseName = File(pdfPath).nameWithoutExtension
            val pdfFileName = "${baseName}_protected_$timestamp.pdf"
            val outputFile = File(documentsDir, pdfFileName)
            
            val stamper = com.itextpdf.text.pdf.PdfStamper(reader, FileOutputStream(outputFile))
            
            stamper.setEncryption(
                password.toByteArray(),
                password.toByteArray(),
                com.itextpdf.text.pdf.PdfWriter.ALLOW_PRINTING,
                com.itextpdf.text.pdf.PdfWriter.ENCRYPTION_AES_128
            )
            
            stamper.close()
            reader.close()
            
            // Save to public storage
            saveToPublicStorage(context, outputFile, pdfFileName)
            
            Log.d(TAG, "Password protected PDF: $pdfFileName")
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to password protect: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Optimize PDF by reducing image quality and recompressing
     */
    fun optimizePdf(
        context: Context,
        pdfPath: String,
        quality: Int = 60  // JPEG quality 1-100 (lower = smaller file)
    ): Result<String> {
        return try {
            val documentsDir = File(context.filesDir, "documents")
            if (!documentsDir.exists()) documentsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseName = File(pdfPath).nameWithoutExtension
            val pdfFileName = "${baseName}_optimized_$timestamp.pdf"
            val outputFile = File(documentsDir, pdfFileName)
            
            // Open the source PDF
            val sourceFile = File(pdfPath)
            val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            
            // Create new compressed PDF
            val document = com.itextpdf.text.Document()
            val writer = com.itextpdf.text.pdf.PdfWriter.getInstance(
                document,
                FileOutputStream(outputFile)
            )
            writer.setFullCompression()
            document.open()
            
            // Process each page
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // Render at reduced scale for compression
                val scale = when {
                    quality < 40 -> 0.5f  // Low quality = half resolution
                    quality < 70 -> 0.75f  // Medium quality
                    else -> 1.0f  // High quality
                }
                
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                // Compress bitmap to JPEG
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val imageBytes = stream.toByteArray()
                bitmap.recycle()
                
                // Add to PDF
                val image = com.itextpdf.text.Image.getInstance(imageBytes)
                
                // Set page size to match image
                val pageRect = com.itextpdf.text.Rectangle(image.scaledWidth, image.scaledHeight)
                document.setPageSize(pageRect)
                document.newPage()
                
                image.setAbsolutePosition(0f, 0f)
                document.add(image)
            }
            
            document.close()
            renderer.close()
            pfd.close()
            
            // Save to public storage
            saveToPublicStorage(context, outputFile, pdfFileName)
            
            val originalSize = sourceFile.length()
            val newSize = outputFile.length()
            val reduction = ((originalSize - newSize) * 100.0 / originalSize).toInt()
            
            Log.d(TAG, "Optimized PDF: $pdfFileName (reduced by $reduction%)")
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to optimize PDF: ${e.message}", e)
            Result.failure(e)
        }
    }
}
