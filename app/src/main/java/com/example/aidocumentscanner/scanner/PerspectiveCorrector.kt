package com.example.aidocumentscanner.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Performs perspective correction (4-point transform) on images.
 * All processing is done locally - no internet required.
 */
object PerspectiveCorrector {
    
    /**
     * Apply perspective transform to crop and straighten document.
     * @param bitmap Source image
     * @param corners Four corners in order: TL, TR, BR, BL
     * @return Cropped and corrected bitmap
     */
    fun correctPerspective(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        // Calculate output dimensions
        val widthTop = distance(corners[0], corners[1])
        val widthBottom = distance(corners[3], corners[2])
        val maxWidth = max(widthTop, widthBottom).toInt()
        
        val heightLeft = distance(corners[0], corners[3])
        val heightRight = distance(corners[1], corners[2])
        val maxHeight = max(heightLeft, heightRight).toInt()
        
        // Source points
        val srcPoints = MatOfPoint2f(
            Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
            Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
            Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
            Point(corners[3].x.toDouble(), corners[3].y.toDouble())
        )
        
        // Destination points
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxWidth.toDouble(), 0.0),
            Point(maxWidth.toDouble(), maxHeight.toDouble()),
            Point(0.0, maxHeight.toDouble())
        )
        
        // Get perspective transform matrix
        val transformMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        
        // Apply transform
        val output = Mat()
        Imgproc.warpPerspective(mat, output, transformMatrix, Size(maxWidth.toDouble(), maxHeight.toDouble()))
        
        // Convert back to bitmap
        val result = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(output, result)
        
        // Clean up
        mat.release()
        output.release()
        srcPoints.release()
        dstPoints.release()
        transformMatrix.release()
        
        return result
    }
    
    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }
}
