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
import java.io.InputStream

class WordProcessor {
    
    suspend fun convertWordToBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // For now, we'll create a placeholder bitmap
            // In a real implementation, you'd use Apache POI or similar library
            // to read Word documents and convert them to images
            
            val fileName = getFileName(context, uri)
            return@withContext createWordDocumentPlaceholder(fileName)
            
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun addStampToWord(
        context: Context,
        wordUri: Uri,
        stampBitmap: Bitmap,
        x: Float,
        y: Float,
        transparency: Float,
        blendMode: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val wordBitmap = convertWordToBitmap(context, wordUri) ?: return@withContext null
            
            // Create final bitmap with stamp
            val finalBitmap = wordBitmap.copy(Bitmap.Config.ARGB_8888, true)
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
                wordBitmap.width.toFloat() / stampBitmap.width,
                wordBitmap.height.toFloat() / stampBitmap.height
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
    
    private fun createWordDocumentPlaceholder(fileName: String): Bitmap {
        val width = 800
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw white background
        canvas.drawColor(android.graphics.Color.WHITE)
        
        // Draw document title
        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 32f
            isAntiAlias = true
            isFakeBoldText = true
        }
        
        canvas.drawText("Word Документ", 50f, 80f, titlePaint)
        
        // Draw file name
        val fileNamePaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 18f
            isAntiAlias = true
        }
        
        canvas.drawText("Файл: $fileName", 50f, 120f, fileNamePaint)
        
        // Draw some sample content
        val contentPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            isAntiAlias = true
        }
        
        val sampleText = """
            Это пример Word документа.
            
            Здесь может быть любой текст, таблицы,
            изображения и другие элементы документа.
            
            Для реальной работы с Word документами
            потребуется библиотека Apache POI или
            аналогичная для Android.
        """.trimIndent()
        
        val lines = sampleText.split("\n")
        var yPosition = 180f
        lines.forEach { line ->
            canvas.drawText(line, 50f, yPosition, contentPaint)
            yPosition += 30f
        }
        
        return bitmap
    }
    
    private fun getFileName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex) ?: "Неизвестный файл"
        } ?: "Неизвестный файл"
    }
}