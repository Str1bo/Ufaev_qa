package com.example.stampprinter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.stampprinter.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var documentUri: Uri? = null
    private var stampBitmap: Bitmap? = null
    private var documentBitmap: Bitmap? = null
    private var stampX = 100f
    private var stampY = 100f
    private var transparency = 0.7f
    private var blendMode = "normal"
    private val pdfProcessor = PdfProcessor()
    private val wordProcessor = WordProcessor()
    
    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                documentUri = uri
                loadDocument(uri)
            }
        }
    }
    
    private val stampPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadStamp(uri)
            }
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permissions granted, proceed with file operations
        } else {
            Toast.makeText(this, "Разрешения необходимы для работы с файлами", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        
        // Setup document selection
        binding.btnSelectDocument.setOnClickListener {
            openDocumentPicker()
        }
        
        // Setup stamp selection
        binding.btnSelectStamp.setOnClickListener {
            openStampPicker()
        }
        
        // Setup transparency slider
        binding.sliderTransparency.addOnChangeListener { _, value, _ ->
            transparency = value
            updateStampOverlay()
        }
        
        // Setup blend mode spinner
        setupBlendModeSpinner()
        
        // Setup save button
        binding.btnSavePdf.setOnClickListener {
            saveDocument()
        }
        
        // Setup document preview touch handling
        setupDocumentPreviewTouch()
    }
    
    private fun setupBlendModeSpinner() {
        val blendModes = arrayOf("normal", "multiply", "screen", "overlay", "darken", "lighten")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, blendModes)
        binding.spinnerBlendMode.setAdapter(adapter)
        
        binding.spinnerBlendMode.setOnItemClickListener { _, _, position, _ ->
            blendMode = blendModes[position]
            updateStampOverlay()
        }
    }
    
    private fun setupDocumentPreviewTouch() {
        binding.ivDocumentPreview.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    stampX = event.x
                    stampY = event.y
                    updateStampOverlay()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    stampX = event.x
                    stampY = event.y
                    updateStampOverlay()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
    
    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ))
        }
        documentPickerLauncher.launch(intent)
    }
    
    private fun openStampPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        stampPickerLauncher.launch(intent)
    }
    
    private fun loadDocument(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.progressIndicator.visibility = View.VISIBLE
                
                val fileName = getFileName(uri)
                binding.tvDocumentName.text = fileName
                
                // Determine document type and load accordingly
                val mimeType = contentResolver.getType(uri)
                documentBitmap = when {
                    mimeType?.contains("pdf") == true -> {
                        pdfProcessor.convertPdfToBitmap(this@MainActivity, uri)
                    }
                    mimeType?.contains("word") == true || mimeType?.contains("document") == true -> {
                        wordProcessor.convertWordToBitmap(this@MainActivity, uri)
                    }
                    else -> {
                        createPlaceholderDocument()
                    }
                }
                
                binding.ivDocumentPreview.setImageBitmap(documentBitmap)
                updateStampOverlay()
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка загрузки документа: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressIndicator.visibility = View.GONE
            }
        }
    }
    
    private fun loadStamp(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.progressIndicator.visibility = View.VISIBLE
                
                val fileName = getFileName(uri)
                binding.tvStampName.text = fileName
                
                stampBitmap = withContext(Dispatchers.IO) {
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                }
                
                binding.ivStampPreview.apply {
                    setImageBitmap(stampBitmap)
                    visibility = View.VISIBLE
                }
                
                updateStampOverlay()
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка загрузки печати: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressIndicator.visibility = View.GONE
            }
        }
    }
    
    private fun updateStampOverlay() {
        if (stampBitmap != null && documentBitmap != null) {
            val overlayBitmap = createStampOverlay()
            binding.ivStampOverlay.apply {
                setImageBitmap(overlayBitmap)
                x = stampX - width / 2f
                y = stampY - height / 2f
                visibility = View.VISIBLE
            }
        }
    }
    
    private fun createStampOverlay(): Bitmap {
        val stamp = stampBitmap ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        
        // Create a copy of the stamp with transparency
        val overlayBitmap = stamp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(overlayBitmap)
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
        
        canvas.drawBitmap(stamp, 0f, 0f, paint)
        return overlayBitmap
    }
    
    private fun saveDocument() {
        if (documentUri == null || stampBitmap == null) {
            Toast.makeText(this, "Выберите документ и печать", Toast.LENGTH_LONG).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                binding.progressIndicator.visibility = View.VISIBLE
                
                val outputFile = createOutputFile()
                val finalBitmap = createFinalDocument()
                
                withContext(Dispatchers.IO) {
                    val outputStream = FileOutputStream(outputFile)
                    finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()
                }
                
                Toast.makeText(this@MainActivity, "Документ сохранен: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressIndicator.visibility = View.GONE
            }
        }
    }
    
    private fun createFinalDocument(): Bitmap {
        val document = documentBitmap ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val stamp = stampBitmap ?: return document
        
        val finalBitmap = document.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(finalBitmap)
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
            document.width.toFloat() / stamp.width,
            document.height.toFloat() / stamp.height
        ) * 0.3f // Make stamp smaller
        
        val scaledStamp = Bitmap.createScaledBitmap(
            stamp,
            (stamp.width * scale).toInt(),
            (stamp.height * scale).toInt(),
            true
        )
        
        canvas.drawBitmap(scaledStamp, stampX, stampY, paint)
        return finalBitmap
    }
    
    private fun createOutputFile(): File {
        val timestamp = System.currentTimeMillis()
        val fileName = "stamped_document_$timestamp.png"
        val outputDir = File(getExternalFilesDir(null), "stamped_documents")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return File(outputDir, fileName)
    }
    
    private fun createPlaceholderDocument(): Bitmap {
        val width = 800
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw white background
        canvas.drawColor(android.graphics.Color.WHITE)
        
        // Draw some placeholder text
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }
        
        canvas.drawText("Документ для наложения печати", 50f, 100f, paint)
        canvas.drawText("Выберите PDF или Word документ", 50f, 150f, paint)
        canvas.drawText("для загрузки реального содержимого", 50f, 200f, paint)
        
        return bitmap
    }
    
    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex) ?: "Неизвестный файл"
        } ?: "Неизвестный файл"
    }
}