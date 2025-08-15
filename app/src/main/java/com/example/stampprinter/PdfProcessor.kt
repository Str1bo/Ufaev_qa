package com.example.stampprinter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.InputStream

class PdfProcessor {
    
    suspend fun convertPdfToBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val document = PDDocument.load(inputStream)
            val renderer = PDFRenderer(document)
            
            // Render first page
            val page = renderer.renderImageWithDPI(0, 300f) // 300 DPI for good quality
            
            // Convert BufferedImage to Android Bitmap
            val bitmap = convertBufferedImageToBitmap(page)
            
            document.close()
            inputStream?.close()
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun addStampToPdf(
        context: Context,
        pdfUri: Uri,
        stampBitmap: Bitmap,
        x: Float,
        y: Float,
        transparency: Float,
        blendMode: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pdfBitmap = convertPdfToBitmap(context, pdfUri) ?: return@withContext null
            
            // Create final bitmap with stamp
            val finalBitmap = pdfBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(finalBitmap)
            val paint = Paint().apply {
                alpha = (255 * transparency).toInt()
            }
            
            // Apply blend mode
            when (blendMode) {
                "multiply" -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                "screen" -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                "overlay" -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                "darken" -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                "lighten" -> paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            }
            
            // Scale stamp to fit document
            val scale = minOf(
                pdfBitmap.width.toFloat() / stampBitmap.width,
                pdfBitmap.height.toFloat() / stampBitmap.height
            ) * 0.3f
            
            val scaledStamp = Bitmap.createScaledBitmap(
                stampBitmap,
                (stampBitmap.width * scale).toInt(),
                (stampBitmap.height * scale).toInt(),
                true
            )
            
            canvas.drawBitmap(scaledStamp, x, y, paint)
            finalBitmap
            
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun convertBufferedImageToBitmap(bufferedImage: java.awt.image.BufferedImage): Bitmap {
        val width = bufferedImage.width
        val height = bufferedImage.height
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        
        return bitmap
    }
}