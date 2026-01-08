package com.sf.urduwriter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sf.urduwriter.databinding.ActivityFontManagerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FontManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFontManagerBinding
    private lateinit var fontAdapter: FontAdapter

    private val pickFontsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let {
                if (it.clipData != null) {
                    for (i in 0 until it.clipData!!.itemCount) {
                        val uri = it.clipData!!.getItemAt(i).uri
                        saveFontFromUri(uri)
                    }
                } else if (it.data != null) {
                    val uri = it.data!!
                    saveFontFromUri(uri)
                }
                loadFonts()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFontManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadFonts()

        binding.fabAddFont.setOnClickListener {
            openFontPicker()
        }
    }

    private fun setupRecyclerView() {
        fontAdapter = FontAdapter(mutableListOf(), onDeleteClick = { fontName ->
            deleteFont(fontName)
        })
        binding.fontsRecyclerView.adapter = fontAdapter
        binding.fontsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadFonts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val builtInFonts = FontManager.getBuiltInFonts(this@FontManagerActivity).map { FontItem(it, false) }
            val userAddedFonts = FontManager.getUserAddedFonts(this@FontManagerActivity).map { FontItem(it, true) }
            val allFonts = builtInFonts + userAddedFonts
            withContext(Dispatchers.Main) {
                fontAdapter.updateData(allFonts)
            }
        }
    }

    private fun openFontPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        val mimeTypes = arrayOf("font/ttf", "font/otf", "application/font-sfnt")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        pickFontsLauncher.launch(intent)
    }

    private fun saveFontFromUri(uri: Uri) {
        try {
            val fileName = getFileName(uri)
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null && fileName != null) {
                val file = File(FileUtils.getUserFontsDir(this), fileName)
                val outputStream = FileOutputStream(file)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
            } else {
                 Toast.makeText(this, getString(R.string.failed_to_get_font_info), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed_to_save_font, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        result = cursor.getString(columnIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            result?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    result = it.substring(cut + 1)
                }
            }
        }
        return result
    }
    
    private fun deleteFont(fontName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val deleted = FontManager.deleteFont(this@FontManagerActivity, fontName)
            withContext(Dispatchers.Main) {
                if (deleted) {
                    Toast.makeText(this@FontManagerActivity, getString(R.string.font_deleted, fontName), Toast.LENGTH_SHORT).show()
                    loadFonts()
                } else {
                    Toast.makeText(this@FontManagerActivity, getString(R.string.failed_to_delete_font, fontName), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

data class FontItem(val name: String, val isDeletable: Boolean)
