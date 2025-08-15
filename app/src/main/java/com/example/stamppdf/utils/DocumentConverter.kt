package com.example.stamppdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

class DocumentConverter {
    
    companion object {
        fun convertToBitmap(context: Context, uri: Uri): Bitmap? {
            return try {
                val mimeType = context.contentResolver.getType(uri)
                when {
                    mimeType == "application/pdf" -> convertPdfToBitmap(context, uri)
                    mimeType?.contains("word") == true -> convertWordToBitmap(context, uri)
                    mimeType?.startsWith("image/") == true -> convertImageToBitmap(context, uri)
                    else -> convertImageToBitmap(context, uri) // Fallback
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        
        private fun convertPdfToBitmap(context: Context, uri: Uri): Bitmap? {
            val input = ParcelFileDescriptor.open(context.contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(input)
            
            return try {
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            } finally {
                renderer.close()
                input.close()
            }
        }
        
        private fun convertWordToBitmap(context: Context, uri: Uri): Bitmap? {
            // For Word documents, we'll create a simple placeholder
            // In a real app, you might want to use Apache POI or similar library
            val placeholderBitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(placeholderBitmap)
            
            // Draw white background
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
            }
            canvas.drawRect(0f, 0f, 800f, 1000f, paint)
            
            // Draw text
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 24f
                isAntiAlias = true
            }
            
            canvas.drawText("Word Document", 50f, 100f, textPaint)
            canvas.drawText("(Converted to image for stamping)", 50f, 150f, textPaint)
            
            return placeholderBitmap
        }
        
        private fun convertImageToBitmap(context: Context, uri: Uri): Bitmap? {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            return bitmap
        }
    }
}