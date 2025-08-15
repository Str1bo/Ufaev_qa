package com.example.stamppdf

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.stamppdf.databinding.ActivityMainBinding
import com.example.stamppdf.utils.PdfProcessor
import com.example.stamppdf.utils.StampPosition
import com.example.stamppdf.utils.DocumentConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedDocumentUri: Uri? = null
    private var selectedStampUri: Uri? = null
    private var documentBitmap: Bitmap? = null
    private var stampBitmap: Bitmap? = null
    private val pdfProcessor = PdfProcessor()

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedDocumentUri = uri
                binding.tvDocumentPath.text = "Документ выбран"
                loadDocumentPreview(uri)
            }
        }
    }

    private val stampPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedStampUri = uri
                loadStampPreview(uri)
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.extras?.get("data")?.let { bitmap ->
                stampBitmap = bitmap as Bitmap
                binding.ivStampPreview.apply {
                    setImageBitmap(bitmap)
                    visibility = View.VISIBLE
                }
                updatePreview()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupListeners()
        checkPermissions()
    }

    private fun setupUI() {
        // Setup position spinner
        val positions = arrayOf(
            "Верхний левый",
            "Верхний правый", 
            "Нижний левый",
            "Нижний правый",
            "Центр"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, positions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPosition.adapter = adapter

        // Setup transparency seekbar
        binding.seekBarTransparency.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvTransparencyValue.text = "$progress%"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun setupListeners() {
        binding.btnSelectDocument.setOnClickListener {
            openDocumentPicker()
        }

        binding.btnSelectStamp.setOnClickListener {
            showStampSelectionDialog()
        }

        binding.btnSavePdf.setOnClickListener {
            savePdf()
        }

        // Listen for mode changes
        binding.radioGroupMode.setOnCheckedChangeListener { _, _ ->
            updatePreview()
        }

        // Listen for position changes
        binding.spinnerPosition.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ))
        }
        documentPickerLauncher.launch(intent)
    }

    private fun showStampSelectionDialog() {
        val options = arrayOf("Выбрать из галереи", "Сделать фото")
        android.app.AlertDialog.Builder(this)
            .setTitle("Выберите способ")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openImagePicker()
                    1 -> openCamera()
                }
            }
            .show()
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        stampPickerLauncher.launch(intent)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    private fun loadDocumentPreview(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = DocumentConverter.convertToBitmap(this@MainActivity, uri)
                
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        documentBitmap = bitmap
                        updatePreview()
                    } else {
                        Toast.makeText(this@MainActivity, "Не удалось загрузить документ", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка загрузки документа: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadStampPreview(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .into(binding.ivStampPreview)
        
        binding.ivStampPreview.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                withContext(Dispatchers.Main) {
                    stampBitmap = bitmap
                    updatePreview()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка загрузки печати: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePreview() {
        if (documentBitmap != null && stampBitmap != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val position = getSelectedPosition()
                    val transparency = binding.seekBarTransparency.progress
                    val isTextMode = binding.rbTextMode.isChecked
                    
                    val previewBitmap = pdfProcessor.createPreview(
                        documentBitmap!!,
                        stampBitmap!!,
                        position,
                        transparency,
                        isTextMode
                    )
                    
                    withContext(Dispatchers.Main) {
                        binding.ivPreview.apply {
                            setImageBitmap(previewBitmap)
                            visibility = View.VISIBLE
                        }
                        binding.tvPreviewPlaceholder.visibility = View.GONE
                        binding.btnSavePdf.isEnabled = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Ошибка создания предварительного просмотра: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getSelectedPosition(): StampPosition {
        return when (binding.spinnerPosition.selectedItemPosition) {
            0 -> StampPosition.TOP_LEFT
            1 -> StampPosition.TOP_RIGHT
            2 -> StampPosition.BOTTOM_LEFT
            3 -> StampPosition.BOTTOM_RIGHT
            4 -> StampPosition.CENTER
            else -> StampPosition.TOP_LEFT
        }
    }

    private fun savePdf() {
        if (selectedDocumentUri == null || stampBitmap == null) {
            Toast.makeText(this, "Выберите документ и печать", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val position = getSelectedPosition()
                val transparency = binding.seekBarTransparency.progress
                val isTextMode = binding.rbTextMode.isChecked
                
                val outputFile = File(getExternalFilesDir(null), "stamped_document.pdf")
                
                pdfProcessor.processDocument(
                    this@MainActivity,
                    selectedDocumentUri!!,
                    stampBitmap!!,
                    position,
                    transparency,
                    isTextMode,
                    outputFile
                )
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "PDF сохранен: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                }
            }
            PERMISSIONS_REQUEST -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Разрешения предоставлены", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val PERMISSIONS_REQUEST = 101
    }
}