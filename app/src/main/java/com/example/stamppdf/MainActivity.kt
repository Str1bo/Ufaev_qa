package com.example.stamppdf

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var documentUri: Uri? = null
    private var stampUri: Uri? = null
    private var documentBitmap: Bitmap? = null
    private var stampBitmap: Bitmap? = null
    private var isDocumentLoaded = false
    private var isStampLoaded = false
    private val executor = Executors.newSingleThreadExecutor()

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
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

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Handle camera result
            // For simplicity, we'll use gallery picker for now
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize PDFBox
        PDFBoxResourceLoader.init(applicationContext)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        binding.btnSelectDocument.setOnClickListener {
            showDocumentPickerDialog()
        }

        binding.btnSelectStamp.setOnClickListener {
            showStampPickerDialog()
        }

        binding.seekBarTransparency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvTransparencyValue.text = "$progress%"
                if (isDocumentLoaded && isStampLoaded) {
                    updatePreview()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.rgPosition.setOnCheckedChangeListener { _, _ ->
            if (isDocumentLoaded && isStampLoaded) {
                updatePreview()
            }
        }

        binding.btnApplyStamp.setOnClickListener {
            if (isDocumentLoaded && isStampLoaded) {
                updatePreview()
            } else {
                Toast.makeText(this, "Сначала выберите документ и печать", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnReset.setOnClickListener {
            resetAll()
        }

        binding.btnSavePDF.setOnClickListener {
            if (isDocumentLoaded && isStampLoaded) {
                saveToPDF()
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
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    private fun showDocumentPickerDialog() {
        val options = arrayOf("PDF документ", "Word документ")
        AlertDialog.Builder(this)
            .setTitle("Выберите тип документа")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickDocument("application/pdf")
                    1 -> pickDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                }
            }
            .show()
    }

    private fun showStampPickerDialog() {
        val options = arrayOf("Камера", "Галерея")
        AlertDialog.Builder(this)
            .setTitle("Выберите источник изображения")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> pickStamp()
                }
            }
            .show()
    }

    private fun pickDocument(mimeType: String) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        documentPickerLauncher.launch(intent)
    }

    private fun pickStamp() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        stampPickerLauncher.launch(intent)
    }

    private fun openCamera() {
        // For simplicity, we'll use gallery picker
        // In a real app, you'd implement camera functionality
        pickStamp()
    }

    private fun loadDocument(uri: Uri) {
        try {
            documentUri = uri
            val inputStream = contentResolver.openInputStream(uri)
            
            if (uri.toString().endsWith(".pdf", true)) {
                loadPDFDocument(inputStream)
            } else {
                loadWordDocument(inputStream)
            }
            
            binding.tvDocumentInfo.text = "Документ загружен: ${getFileName(uri)}"
            isDocumentLoaded = true
            updateSaveButton()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки документа: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPDFDocument(inputStream: InputStream?) {
        executor.execute {
            try {
                val document = PDDocument.load(inputStream)
                val renderer = PDFRenderer(document)
                val page = renderer.renderImageWithDPI(0, 300f)
                documentBitmap = page
                document.close()
                
                runOnUiThread {
                    updateDocumentPreview()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка загрузки PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadWordDocument(inputStream: InputStream?) {
        // For simplicity, we'll create a placeholder bitmap
        // In a real app, you'd use Apache POI to convert Word to PDF first
        executor.execute {
            try {
                val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, 800f, 1000f, paint)
                
                paint.color = Color.BLACK
                paint.textSize = 24f
                canvas.drawText("Word Document", 50f, 100f, paint)
                canvas.drawText("(Converted to PDF)", 50f, 150f, paint)
                
                documentBitmap = bitmap
                
                runOnUiThread {
                    updateDocumentPreview()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка загрузки Word документа: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadStamp(uri: Uri) {
        try {
            stampUri = uri
            Glide.with(this)
                .load(uri)
                .into(binding.ivStampPreview)
            
            binding.ivStampPreview.visibility = View.VISIBLE
            binding.tvStampInfo.text = "Печать загружена: ${getFileName(uri)}"
            isStampLoaded = true
            updateSaveButton()
            
            // Load stamp bitmap for processing
            val inputStream = contentResolver.openInputStream(uri)
            stampBitmap = BitmapFactory.decodeStream(inputStream)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки печати: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDocumentPreview() {
        documentBitmap?.let { bitmap ->
            binding.ivDocumentPreview.setImageBitmap(bitmap)
            binding.ivDocumentPreview.visibility = View.VISIBLE
            binding.tvPreviewPlaceholder.visibility = View.GONE
        }
    }

    private fun updatePreview() {
        if (documentBitmap == null || stampBitmap == null) return

        executor.execute {
            try {
                val resultBitmap = applyStampToDocument()
                runOnUiThread {
                    binding.ivDocumentPreview.setImageBitmap(resultBitmap)
                    binding.ivStampOverlay.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка обновления предварительного просмотра: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyStampToDocument(): Bitmap {
        val document = documentBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
        val stamp = stampBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(document)

        // Get position
        val position = when (binding.rgPosition.checkedRadioButtonId) {
            R.id.rbTopLeft -> Position.TOP_LEFT
            R.id.rbTopRight -> Position.TOP_RIGHT
            R.id.rbBottomLeft -> Position.BOTTOM_LEFT
            R.id.rbBottomRight -> Position.BOTTOM_RIGHT
            R.id.rbCenter -> Position.CENTER
            else -> Position.TOP_LEFT
        }

        // Calculate stamp position
        val stampWidth = stamp.width * 0.3f // Scale stamp to 30% of original size
        val stampHeight = stamp.height * 0.3f
        val margin = 50f

        val (x, y) = when (position) {
            Position.TOP_LEFT -> Pair(margin, margin)
            Position.TOP_RIGHT -> Pair(document.width - stampWidth - margin, margin)
            Position.BOTTOM_LEFT -> Pair(margin, document.height - stampHeight - margin)
            Position.BOTTOM_RIGHT -> Pair(document.width - stampWidth - margin, document.height - stampHeight - margin)
            Position.CENTER -> Pair((document.width - stampWidth) / 2, (document.height - stampHeight) / 2)
        }

        // Apply transparency
        val transparency = binding.seekBarTransparency.progress
        val paint = Paint().apply {
            alpha = (255 * (100 - transparency) / 100)
        }

        canvas.drawBitmap(stamp, null, RectF(x, y, x + stampWidth, y + stampHeight), paint)
        return document
    }

    private fun saveToPDF() {
        executor.execute {
            try {
                val outputFile = File(getExternalFilesDir(null), "stamped_document.pdf")
                val document = PDDocument()
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)

                val contentStream = PDPageContentStream(document, page)
                
                // Convert bitmap to PDF
                val resultBitmap = applyStampToDocument()
                val byteArrayOutputStream = ByteArrayOutputStream()
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()
                
                val image = PDImageXObject.createFromByteArray(document, imageBytes, "stamped_document")
                contentStream.drawImage(image, 0, 0, page.mediaBox.width, page.mediaBox.height)
                
                contentStream.close()
                document.save(outputFile)
                document.close()

                runOnUiThread {
                    Toast.makeText(this, "PDF сохранен: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка сохранения PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resetAll() {
        documentUri = null
        stampUri = null
        documentBitmap = null
        stampBitmap = null
        isDocumentLoaded = false
        isStampLoaded = false

        binding.tvDocumentInfo.text = "Документ не выбран"
        binding.tvStampInfo.text = "Печать не выбрана"
        binding.ivStampPreview.visibility = View.GONE
        binding.ivDocumentPreview.visibility = View.GONE
        binding.tvPreviewPlaceholder.visibility = View.VISIBLE
        binding.seekBarTransparency.progress = 50
        binding.rgPosition.check(R.id.rbTopLeft)
        updateSaveButton()
    }

    private fun updateSaveButton() {
        binding.btnSavePDF.isEnabled = isDocumentLoaded && isStampLoaded
    }

    private fun getFileName(uri: Uri): String {
        return uri.lastPathSegment ?: "Неизвестный файл"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted
            } else {
                Toast.makeText(this, "Требуются разрешения для работы с файлами", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    enum class Position {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }
}