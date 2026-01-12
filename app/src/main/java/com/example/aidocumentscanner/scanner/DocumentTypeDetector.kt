package com.example.aidocumentscanner.scanner

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Document Type Detector using image analysis.
 * Auto-detects document types for smart enhancement presets.
 * All processing done offline using OpenCV.
 */
object DocumentTypeDetector {
    
    enum class DocumentType(val label: String, val description: String) {
        ID_CARD("ID Card", "Small rectangular document"),
        NOTES("Notes", "Handwritten or printed notes"),
        BOOK("Book", "Book page or spread"),
        RECEIPT("Receipt", "Long narrow receipt"),
        FORM("Form", "Form with fields"),
        BUSINESS_CARD("Business Card", "Small card format"),
        UNKNOWN("Document", "General document")
    }
    
    data class EnhancementSettings(
        val contrast: Float = 1.0f,
        val brightness: Int = 0,
        val sharpness: Float = 1.0f,
        val denoise: Boolean = false,
        val removeBackground: Boolean = false,
        val blackAndWhite: Boolean = false,
        val autoRotate: Boolean = true
    )
    
    data class DetectionResult(
        val type: DocumentType,
        val confidence: Float,
        val settings: EnhancementSettings
    )
    
    /**
     * Detect document type from bitmap
     */
    fun detectType(bitmap: Bitmap): DetectionResult {
        return try {
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val textDensity = analyzeTextDensity(bitmap)
            val colorVariance = analyzeColorVariance(bitmap)
            val hasLines = detectFormLines(bitmap)
            
            // Decision logic based on analysis
            val type = when {
                // Receipt: Long and narrow (aspect ratio < 0.5 or > 2)
                aspectRatio < 0.4f || aspectRatio > 2.5f -> DocumentType.RECEIPT
                
                // ID Card / Business Card: Small with specific aspect ratios (credit card ratio ~1.58)
                aspectRatio in 1.4f..1.8f && bitmap.width < 2000 -> {
                    if (colorVariance > 50) DocumentType.ID_CARD else DocumentType.BUSINESS_CARD
                }
                
                // Form: Has horizontal lines and structured layout
                hasLines && textDensity < 0.4f -> DocumentType.FORM
                
                // Book: Square-ish aspect ratio, high text density
                aspectRatio in 0.6f..0.9f && textDensity > 0.5f -> DocumentType.BOOK
                
                // Notes: High text density, varied line patterns
                textDensity > 0.6f && !hasLines -> DocumentType.NOTES
                
                else -> DocumentType.UNKNOWN
            }
            
            val confidence = calculateConfidence(type, aspectRatio, textDensity, colorVariance, hasLines)
            val settings = getEnhancementPreset(type)
            
            DetectionResult(type, confidence, settings)
        } catch (e: Exception) {
            DetectionResult(DocumentType.UNKNOWN, 0.5f, getEnhancementPreset(DocumentType.UNKNOWN))
        }
    }
    
    /**
     * Get enhancement preset for document type
     */
    fun getEnhancementPreset(type: DocumentType): EnhancementSettings {
        return when (type) {
            DocumentType.ID_CARD -> EnhancementSettings(
                contrast = 1.3f,
                brightness = 10,
                sharpness = 1.2f,
                denoise = true,
                removeBackground = false,
                blackAndWhite = false
            )
            DocumentType.NOTES -> EnhancementSettings(
                contrast = 1.4f,
                brightness = 20,
                sharpness = 1.5f,
                denoise = false,
                removeBackground = true,
                blackAndWhite = false
            )
            DocumentType.BOOK -> EnhancementSettings(
                contrast = 1.3f,
                brightness = 15,
                sharpness = 1.3f,
                denoise = true,
                removeBackground = true,
                blackAndWhite = false
            )
            DocumentType.RECEIPT -> EnhancementSettings(
                contrast = 1.5f,
                brightness = 25,
                sharpness = 1.2f,
                denoise = true,
                removeBackground = true,
                blackAndWhite = true
            )
            DocumentType.FORM -> EnhancementSettings(
                contrast = 1.4f,
                brightness = 15,
                sharpness = 1.1f,
                denoise = true,
                removeBackground = true,
                blackAndWhite = false
            )
            DocumentType.BUSINESS_CARD -> EnhancementSettings(
                contrast = 1.2f,
                brightness = 5,
                sharpness = 1.3f,
                denoise = true,
                removeBackground = false,
                blackAndWhite = false
            )
            DocumentType.UNKNOWN -> EnhancementSettings(
                contrast = 1.2f,
                brightness = 10,
                sharpness = 1.1f,
                denoise = false,
                removeBackground = false,
                blackAndWhite = false
            )
        }
    }
    
    /**
     * Analyze text density in the image
     */
    private fun analyzeTextDensity(bitmap: Bitmap): Float {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            // Convert to grayscale
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            // Apply adaptive threshold
            val thresh = Mat()
            Imgproc.adaptiveThreshold(
                gray, thresh, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 11, 2.0
            )
            
            // Count non-zero pixels (text pixels)
            val textPixels = Core.countNonZero(thresh)
            val totalPixels = thresh.rows() * thresh.cols()
            
            // Clean up
            mat.release()
            gray.release()
            thresh.release()
            
            textPixels.toFloat() / totalPixels.toFloat()
        } catch (e: Exception) {
            0.3f // Default medium density
        }
    }
    
    /**
     * Analyze color variance in the image
     */
    private fun analyzeColorVariance(bitmap: Bitmap): Float {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            // Convert to HSV
            val hsv = Mat()
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
            
            // Split channels
            val channels = ArrayList<Mat>()
            Core.split(hsv, channels)
            
            // Calculate standard deviation of saturation channel
            val mean = MatOfDouble()
            val std = MatOfDouble()
            Core.meanStdDev(channels[1], mean, std)
            
            val variance = std.get(0, 0)[0].toFloat()
            
            // Clean up
            mat.release()
            hsv.release()
            channels.forEach { it.release() }
            mean.release()
            std.release()
            
            variance
        } catch (e: Exception) {
            30f // Default medium variance
        }
    }
    
    /**
     * Detect horizontal lines (form fields)
     */
    private fun detectFormLines(bitmap: Bitmap): Boolean {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            // Convert to grayscale
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            // Apply edge detection
            val edges = Mat()
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            
            // Detect lines
            val lines = Mat()
            Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 100, 100.0, 10.0)
            
            // Count horizontal lines
            var horizontalLines = 0
            for (i in 0 until lines.rows()) {
                val line = lines.get(i, 0)
                val y1 = line[1]
                val y2 = line[3]
                if (abs(y1 - y2) < 10) { // Nearly horizontal
                    horizontalLines++
                }
            }
            
            // Clean up
            mat.release()
            gray.release()
            edges.release()
            lines.release()
            
            horizontalLines > 5 // Has significant horizontal lines
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Calculate confidence score for detection
     */
    private fun calculateConfidence(
        type: DocumentType,
        aspectRatio: Float,
        textDensity: Float,
        colorVariance: Float,
        hasLines: Boolean
    ): Float {
        return when (type) {
            DocumentType.RECEIPT -> {
                if (aspectRatio < 0.3f || aspectRatio > 3f) 0.9f else 0.7f
            }
            DocumentType.ID_CARD -> {
                if (aspectRatio in 1.5f..1.7f && colorVariance > 40) 0.85f else 0.6f
            }
            DocumentType.FORM -> {
                if (hasLines && textDensity < 0.3f) 0.8f else 0.6f
            }
            DocumentType.BOOK -> {
                if (aspectRatio in 0.65f..0.85f && textDensity > 0.5f) 0.75f else 0.5f
            }
            DocumentType.NOTES -> {
                if (textDensity > 0.6f) 0.7f else 0.5f
            }
            else -> 0.5f
        }
    }
}
