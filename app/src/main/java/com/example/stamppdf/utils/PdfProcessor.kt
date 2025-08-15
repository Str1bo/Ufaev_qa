package com.example.stamppdf.utils

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class PdfProcessor {
    
    init {
        // Initialize PDFBox
        try {
            PDFBoxResourceLoader.init(null)
        } catch (e: Exception) {
            // Already initialized
        }
    }

    fun createPreview(
        documentBitmap: Bitmap,
        stampBitmap: Bitmap,
        position: StampPosition,
        transparency: Int,
        isTextMode: Boolean
    ): Bitmap {
        val resultBitmap = documentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        
        val stampWidth = (documentBitmap.width * 0.2).toInt()
        val stampHeight = (stampWidth * stampBitmap.height / stampBitmap.width.toFloat()).toInt()
        
        val scaledStamp = Bitmap.createScaledBitmap(stampBitmap, stampWidth, stampHeight, true)
        
        val paint = Paint().apply {
            alpha = (255 * (100 - transparency) / 100)
            if (isTextMode) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            } else {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            }
        }
        
        val x = when (position) {
            StampPosition.TOP_LEFT, StampPosition.BOTTOM_LEFT -> 50
            StampPosition.TOP_RIGHT, StampPosition.BOTTOM_RIGHT -> documentBitmap.width - stampWidth - 50
            StampPosition.CENTER -> (documentBitmap.width - stampWidth) / 2
        }
        
        val y = when (position) {
            StampPosition.TOP_LEFT, StampPosition.TOP_RIGHT -> 50
            StampPosition.BOTTOM_LEFT, StampPosition.BOTTOM_RIGHT -> documentBitmap.height - stampHeight - 50
            StampPosition.CENTER -> (documentBitmap.height - stampHeight) / 2
        }
        
        canvas.drawBitmap(scaledStamp, x.toFloat(), y.toFloat(), paint)
        
        return resultBitmap
    }

    fun processDocument(
        context: Context,
        documentUri: Uri,
        stampBitmap: Bitmap,
        position: StampPosition,
        transparency: Int,
        isTextMode: Boolean,
        outputFile: File
    ) {
        val mimeType = context.contentResolver.getType(documentUri)
        
        when {
            mimeType == "application/pdf" -> {
                processPdfDocument(context, documentUri, stampBitmap, position, transparency, isTextMode, outputFile)
            }
            mimeType?.contains("word") == true -> {
                processWordDocument(context, documentUri, stampBitmap, position, transparency, isTextMode, outputFile)
            }
            else -> {
                // Treat as image
                processImageDocument(context, documentUri, stampBitmap, position, transparency, isTextMode, outputFile)
            }
        }
    }

    private fun processPdfDocument(
        context: Context,
        documentUri: Uri,
        stampBitmap: Bitmap,
        position: StampPosition,
        transparency: Int,
        isTextMode: Boolean,
        outputFile: File
    ) {
        val inputStream = context.contentResolver.openInputStream(documentUri)
        val document = PDDocument.load(inputStream)
        inputStream?.close()
        
        try {
            val stampImage = convertBitmapToPDImageXObject(document, stampBitmap)
            
            for (pageIndex in 0 until document.numberOfPages) {
                val page = document.getPage(pageIndex)
                val contentStream = PDPageContentStream(
                    document, page, 
                    PDPageContentStream.AppendMode.APPEND, true, true
                )
                
                val pageWidth = page.mediaBox.width
                val pageHeight = page.mediaBox.height
                
                val stampWidth = pageWidth * 0.2f
                val stampHeight = stampWidth * stampBitmap.height / stampBitmap.width
                
                val x = when (position) {
                    StampPosition.TOP_LEFT, StampPosition.BOTTOM_LEFT -> 50f
                    StampPosition.TOP_RIGHT, StampPosition.BOTTOM_RIGHT -> pageWidth - stampWidth - 50f
                    StampPosition.CENTER -> (pageWidth - stampWidth) / 2
                }
                
                val y = when (position) {
                    StampPosition.TOP_LEFT, StampPosition.TOP_RIGHT -> pageHeight - 50f
                    StampPosition.BOTTOM_LEFT, StampPosition.BOTTOM_RIGHT -> 50f + stampHeight
                    StampPosition.CENTER -> (pageHeight + stampHeight) / 2
                }
                
                // Set transparency
                val alpha = (100 - transparency) / 100f
                contentStream.setNonStrokingColor(Color(0f, 0f, 0f, alpha))
                
                contentStream.drawImage(stampImage, x, y - stampHeight, stampWidth, stampHeight)
                contentStream.close()
            }
            
            document.save(outputFile)
            document.close()
            
        } catch (e: Exception) {
            document.close()
            throw e
        }
    }

    private fun processWordDocument(
        context: Context,
        documentUri: Uri,
        stampBitmap: Bitmap,
        position: StampPosition,
        transparency: Int,
        isTextMode: Boolean,
        outputFile: File
    ) {
        // For Word documents, we'll convert to PDF first
        // This is a simplified approach - in a real app you might want to use Apache POI
        val documentBitmap = createDocumentBitmap(context, documentUri)
        val resultBitmap = createPreview(documentBitmap, stampBitmap, position, transparency, isTextMode)
        
        // Convert bitmap to PDF
        val document = PDDocument()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        
        val contentStream = PDPageContentStream(document, page)
        val image = convertBitmapToPDImageXObject(document, resultBitmap)
        
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height
        val imageWidth = resultBitmap.width
        val imageHeight = resultBitmap.height
        
        val scale = minOf(pageWidth / imageWidth, pageHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        
        val x = (pageWidth - scaledWidth) / 2
        val y = (pageHeight - scaledHeight) / 2
        
        contentStream.drawImage(image, x, y, scaledWidth, scaledHeight)
        contentStream.close()
        
        document.save(outputFile)
        document.close()
    }

    private fun processImageDocument(
        context: Context,
        documentUri: Uri,
        stampBitmap: Bitmap,
        position: StampPosition,
        transparency: Int,
        isTextMode: Boolean,
        outputFile: File
    ) {
        val documentBitmap = createDocumentBitmap(context, documentUri)
        val resultBitmap = createPreview(documentBitmap, stampBitmap, position, transparency, isTextMode)
        
        // Convert bitmap to PDF
        val document = PDDocument()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        
        val contentStream = PDPageContentStream(document, page)
        val image = convertBitmapToPDImageXObject(document, resultBitmap)
        
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height
        val imageWidth = resultBitmap.width
        val imageHeight = resultBitmap.height
        
        val scale = minOf(pageWidth / imageWidth, pageHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        
        val x = (pageWidth - scaledWidth) / 2
        val y = (pageHeight - scaledHeight) / 2
        
        contentStream.drawImage(image, x, y, scaledWidth, scaledHeight)
        contentStream.close()
        
        document.save(outputFile)
        document.close()
    }

    private fun createDocumentBitmap(context: Context, documentUri: Uri): Bitmap {
        val inputStream = context.contentResolver.openInputStream(documentUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        
        if (bitmap == null) {
            throw IllegalArgumentException("Не удалось загрузить документ как изображение")
        }
        
        return bitmap
    }

    private fun convertBitmapToPDImageXObject(document: PDDocument, bitmap: Bitmap): PDImageXObject {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val imageBytes = stream.toByteArray()
        stream.close()
        
        return JPEGFactory.createFromByteArray(document, imageBytes)
    }
}