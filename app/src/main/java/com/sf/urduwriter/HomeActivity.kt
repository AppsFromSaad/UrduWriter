package com.sf.urduwriter

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sf.urduwriter.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var docAdapter: DocumentAdapter

    private val backupFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveBackupFolder(uri)
                performBackup(uri)
            }
        }
    }

    private val restoreFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                performRestore(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "اردو رائٹر"

        // Set custom font across the layout
        try {
            val jameelNooriFont = Typeface.createFromAsset(assets, "fonts/Jameel_noori_nastaleeq.ttf")
            binding.recentDocumentsLabel.typeface = jameelNooriFont
            
            // Apply font to toolbar title
            binding.toolbar.post {
                for (i in 0 until binding.toolbar.childCount) {
                    val view = binding.toolbar.getChildAt(i)
                    if (view is TextView) {
                        view.typeface = jameelNooriFont
                    }
                }
            }
            
            // Apply to the main content area
            applyFontToViewGroup(binding.root as android.view.ViewGroup, jameelNooriFont)
        } catch (e: Exception) {
            // Font not found
        }

        binding.newDocumentButtonCard.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }

        binding.fontManagerButtonCard.setOnClickListener {
            startActivity(Intent(this, FontManagerActivity::class.java))
        }

        setupRecyclerView()
    }

    private fun applyFontToViewGroup(viewGroup: android.view.ViewGroup, typeface: Typeface) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) {
                child.typeface = typeface
            } else if (child is android.view.ViewGroup) {
                applyFontToViewGroup(child, typeface)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDocuments()
    }

    private fun setupRecyclerView() {
        docAdapter = DocumentAdapter(this::onDocumentClicked, this::onDeleteClicked, this::onShareClicked)
        binding.documentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = docAdapter
        }
    }

    private fun loadDocuments() {
        val docsDir = File(filesDir, "docs")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }
        val docFiles = docsDir.listFiles { file -> file.extension == "html" }?.toList() ?: emptyList()
        val documents = docFiles.map {
            Document(it.nameWithoutExtension, it.absolutePath, it.lastModified())
        }.sortedByDescending { it.lastModified }
        docAdapter.submitList(documents)
    }

    private fun onDocumentClicked(document: Document) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_DOC_PATH, document.path)
        }
        startActivity(intent)
    }

    private fun onDeleteClicked(document: Document) {
        val file = File(document.path)
        file.delete()
        loadDocuments()
    }

    private fun onShareClicked(document: Document) {
        val file = File(document.path)
        if (file.exists()) {
            val htmlContent = file.readText()
            shareAsWordDoc(document.name, htmlContent)
        }
    }

    private fun shareAsWordDoc(fileName: String, htmlContent: String) {
        try {
            val docFile = File(cacheDir, "$fileName.doc")
            
            // Decode Javascript Unicode escapes that might be in the file (e.g. \u003c -> <)
            val decodedHtml = htmlContent.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { matchResult ->
                matchResult.groupValues[1].toInt(16).toChar().toString()
            }.replace("\\n", "\n").replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\")
            
            val wordHtml = """
                <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
                <head>
                    <meta charset='utf-8'>
                    <title>$fileName</title>
                    <style>
                        body {
                            direction: rtl;
                            text-align: right;
                            font-family: 'Jameel Noori Nastaleeq', 'Jameel_noori_nastaleeq', Arial, sans-serif;
                            font-size: 22px;
                        }
                    </style>
                </head>
                <body dir="rtl">
                $decodedHtml
                </body>
                </html>
            """.trimIndent()
            
            docFile.writeText(wordHtml)

            val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.provider", docFile)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/msword"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "شیئر کریں (Share)"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "شیئر کرنے میں مسئلہ: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // --- Options Menu & Backup/Restore Logic ---

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_backup -> {
                backupDocuments()
                true
            }
            R.id.action_restore -> {
                restoreDocuments()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun backupDocuments() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        backupFolderLauncher.launch(intent)
    }

    private fun restoreDocuments() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        restoreFolderLauncher.launch(intent)
    }

    private fun saveBackupFolder(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            val sharedPref = getSharedPreferences("UrduWriterPrefs", android.content.Context.MODE_PRIVATE)
            sharedPref.edit().putString("backup_folder_uri", uri.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun performBackup(uri: Uri) {
        val pickedDir = DocumentFile.fromTreeUri(this, uri)
        if (pickedDir == null || !pickedDir.canWrite()) {
            Toast.makeText(this, "منتخب کردہ فولڈر میں لکھنے کی اجازت نہیں ہے", Toast.LENGTH_LONG).show()
            return
        }

        val docsDir = File(filesDir, "docs")
        val docFiles = docsDir.listFiles { file -> file.extension == "html" } ?: emptyArray()

        if (docFiles.isEmpty()) {
            Toast.makeText(this, "بیک اپ کے لیے کوئی دستاویزات نہیں ہیں", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var successCount = 0
            for (file in docFiles) {
                try {
                    val existingFile = pickedDir.findFile(file.name)
                    if (existingFile != null) {
                        existingFile.delete()
                    }
                    val newFile = pickedDir.createFile("text/html", file.name)
                    if (newFile != null) {
                        contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            file.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        successCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@HomeActivity, "$successCount تحریریں کامیابی سے بیک اپ ہوگئیں!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performRestore(uri: Uri) {
        val pickedDir = DocumentFile.fromTreeUri(this, uri)
        if (pickedDir == null) {
            Toast.makeText(this, "فولڈر لوڈ نہیں ہو سکا", Toast.LENGTH_SHORT).show()
            return
        }

        val files = pickedDir.listFiles()
        val htmlFiles = mutableListOf<DocumentFile>()
        for (f in files) {
            val name = f.name
            if (name != null && name.endsWith(".html")) {
                htmlFiles.add(f)
            }
        }

        if (htmlFiles.isEmpty()) {
            Toast.makeText(this, "اس فولڈر میں کوئی بیک اپ فائلیں نہیں ملیں", Toast.LENGTH_LONG).show()
            return
        }

        val docsDir = File(filesDir, "docs")
        if (!docsDir.exists()) docsDir.mkdirs()

        lifecycleScope.launch(Dispatchers.IO) {
            var successCount = 0
            for (docFile in htmlFiles) {
                try {
                    val name = docFile.name ?: continue
                    val destFile = File(docsDir, name)
                    contentResolver.openInputStream(docFile.uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@HomeActivity, "$successCount تحریریں کامیابی سے بحال ہوگئیں!", Toast.LENGTH_LONG).show()
                loadDocuments()
            }
        }
    }
}