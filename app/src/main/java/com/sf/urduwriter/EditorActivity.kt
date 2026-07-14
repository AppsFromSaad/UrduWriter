package com.sf.urduwriter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.text.InputType
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.dhaval2404.colorpicker.ColorPickerDialog
import com.github.dhaval2404.colorpicker.model.ColorShape
import com.sf.urduwriter.databinding.ActivityEditorBinding
import android.os.CancellationSignal
import android.print.PageRange
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.FileOutputStream
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOC_PATH = "extra_doc_path"
    }

    private lateinit var binding: ActivityEditorBinding
    private lateinit var mEditor: WebView
    private var currentDocPath: String? = null
    private var allFonts = mutableListOf<String>()
    private var loadedHtml: String = ""
    private var isDiscardingChanges = false

    // --- Lifecycle and Setup ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.editor) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = Math.max(systemBars.bottom, ime.bottom)

            val params =
                v.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.bottomMargin = bottomInset
            v.layoutParams = params
            insets
        }

        setupEditor()
        setupToolbar()
        setupFileMenu()
        loadFontList()

        // Apply Nastaliq font to title and layout asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jameelNooriFont =
                    FontManager.getFont(this@EditorActivity, "fonts/Jameel_noori_nastaleeq.ttf")
                if (jameelNooriFont != null) {
                    withContext(Dispatchers.Main) {
                        binding.toolbarTitle.typeface = jameelNooriFont
                        binding.fileButton.typeface = jameelNooriFont
                    }
                }
            } catch (e: Exception) {
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Handle back press to check for unsaved changes
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                checkChangesAndExit()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (!isDiscardingChanges) {
            saveDocumentSilently()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupEditor() {
        mEditor = binding.editor

        // Disable cache to ensure asset changes take effect immediately
        mEditor.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        mEditor.clearCache(true)
        
        mEditor.settings.javaScriptEnabled = true
        mEditor.settings.allowFileAccess = true
        mEditor.addJavascriptInterface(WebAppInterface(), "Android")
        mEditor.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // Let HTML handle RTL to avoid double-processing conflicts
        // mEditor.textDirection = View.TEXT_DIRECTION_RTL 

        // Important for A4 210mm fixed width content
        mEditor.settings.useWideViewPort = true
        mEditor.settings.loadWithOverviewMode = true // Initial fit
        
        // Zoom and Scroll configuration
        mEditor.settings.setSupportZoom(true)
        mEditor.settings.builtInZoomControls = true
        mEditor.settings.displayZoomControls = false
        
        mEditor.isVerticalScrollBarEnabled = true
        mEditor.isHorizontalScrollBarEnabled = true
        mEditor.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

        mEditor.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectAllFontsCSS()
                mEditor.evaluateJavascript("javascript:setDefaultFontSize('22px');", null)

                // Load and apply page settings on page finished
                val sharedPref = getSharedPreferences("UrduWriterPrefs", MODE_PRIVATE)
                val pageSize = sharedPref.getString("pref_page_size", "A4") ?: "A4"
                val pageMargin = sharedPref.getString("pref_page_margin", "0.75in") ?: "0.75in"
                val isLandscape = sharedPref.getBoolean("pref_page_landscape", false)
                val customWidth = sharedPref.getFloat("pref_custom_page_width", 8.27f)
                val customHeight = sharedPref.getFloat("pref_custom_page_height", 11.69f)
                mEditor.evaluateJavascript(
                    "javascript:setPageSettings('$pageSize', '$pageMargin', false, 0, $isLandscape, $customWidth, $customHeight);",
                    null
                )

                loadDocument()
            }
        }

        mEditor.loadUrl("file:///android_asset/editor/index_html_editor.html")
    }

    private fun setupToolbar() {
        binding.boldButton.setOnClickListener { execJs("execCmd", "bold") }
        binding.italicButton.setOnClickListener { execJs("execCmd", "italic") }
        binding.underlineButton.setOnClickListener { execJs("execCmd", "underline") }
        binding.undoButton.setOnClickListener { execJs("execCmd", "undo") }
        binding.redoButton.setOnClickListener { execJs("execCmd", "redo") }

        binding.alignLeftButton.setOnClickListener { execJs("execCmd", "justifyLeft") }
        binding.alignCenterButton.setOnClickListener { execJs("execCmd", "justifyCenter") }
        binding.alignRightButton.setOnClickListener { execJs("execCmd", "justifyRight") }
        binding.alignJustifyButton.setOnClickListener { execJs("execCmd", "justifyFull") }

        binding.bulletsButton.setOnClickListener { execJs("execCmd", "insertUnorderedList") }
        binding.bulletsButton.setOnLongClickListener {
            showBulletDialog()
            true
        }
        binding.numbersButton.setOnClickListener { execJs("execCmd", "insertOrderedList") }

        binding.fontSelectButton.setOnClickListener { showFontSelectionDialog() }
        binding.symbolsButton.setOnClickListener { showSymbolsDialog() }
        binding.colorPickerButton.setOnClickListener { showColorPickerDialog() }
        binding.fontSizeButton.setOnClickListener { showFontSizeDialog() }
        binding.pageLayoutButton.setOnClickListener { showPageLayoutDialog() }
    }

    private fun setupFileMenu() {
        binding.fileButton.setOnClickListener {
            val options = arrayOf(
                getString(R.string.save),
                "نئے نام سے محفوظ کریں",
                "صفحہ کی ترتیب (Page Layout)",
                getString(R.string.save_as_pdf)
            )

            AlertDialog.Builder(this)
                .setTitle("فائل")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> saveDocument(false)
                        1 -> saveDocument(true) // Force new name
                        2 -> showPageLayoutDialog()
                        3 -> showPdfExportOptions()
                    }
                }
                .show()
        }
    }

    private fun loadFontList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val builtIn = assets.list("fonts")?.map { it.substringBeforeLast(".") } ?: emptyList()
            val userFontsDir = File(filesDir, "user_fonts")
            val userFonts = userFontsDir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()

            allFonts.clear()
            allFonts.add("Default")
            allFonts.addAll((builtIn + userFonts).distinct())
        }
    }

    // --- Document Handling ---

    private fun loadDocument() {
        currentDocPath = intent.getStringExtra(EXTRA_DOC_PATH)
        val path = currentDocPath
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    var content = file.readText()

                    // Repair corrupted HTML if it contains escaped unicode patterns from previous bugs
                    if (content.contains(
                            "\\u003c",
                            ignoreCase = true
                        ) || content.contains("\\u003e", ignoreCase = true)
                    ) {
                        content = repairCorruptedHtml(content)
                        try {
                            file.writeText(content)
                            backupToExternalFolder(file.name, content)
                        } catch (_: Exception) {
                        }
                    }
                    
                    loadedHtml = content
                    withContext(Dispatchers.Main) {
                        // Encode to Base64 to prevent any JavaScript syntax/character escape issues
                        val base64 = android.util.Base64.encodeToString(
                            content.toByteArray(Charsets.UTF_8),
                            android.util.Base64.NO_WRAP
                        )
                        mEditor.evaluateJavascript("javascript:setDocumentHtmlBase64('$base64');", null)
                    }
                }
            }
        } else {
            loadedHtml = ""
            mEditor.evaluateJavascript("javascript:setDocumentHtmlBase64('');", null)
        }
    }

    private fun repairCorruptedHtml(corrupted: String): String {
        var result = corrupted
        // Unescape unicode sequences like \u003C or \u003E
        val unicodeRegex = """\\u([0-9a-fA-F]{4})""".toRegex()
        result = unicodeRegex.replace(result) { matchResult ->
            val hexVal = matchResult.groupValues[1]
            try {
                hexVal.toInt(16).toChar().toString()
            } catch (e: Exception) {
                matchResult.value
            }
        }
        // Also replace other common escapes that might have been left over
        result = result
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
        return result
    }

    private fun unescapeJsonString(json: String?): String {
        if (json == null || json == "null" || json.isEmpty()) return ""
        val reader = JsonReader(java.io.StringReader(json))
        reader.isLenient = true
        return try {
            if (reader.peek() == JsonToken.STRING) {
                reader.nextString()
            } else {
                if (json.startsWith("\"") && json.endsWith("\"") && json.length >= 2) {
                    json.substring(1, json.length - 1)
                } else {
                    json
                }
            }
        } catch (e: Exception) {
            if (json.startsWith("\"") && json.endsWith("\"") && json.length >= 2) {
                json.substring(1, json.length - 1)
            } else {
                json
            }
        } finally {
            try {
                reader.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun saveDocument(forceNewName: Boolean, onSaved: (() -> Unit)? = null) {
        mEditor.evaluateJavascript("javascript:getDocumentHtml();") { html ->
            val unescapedHtml = unescapeJsonString(html)

            if (currentDocPath != null && !forceNewName) {
                // Save to existing file
                saveHtmlToFile(unescapedHtml, currentDocPath!!, onSaved)
            } else {
                // Ask for a new name
                askForFileNameAndSave(unescapedHtml, onSaved)
            }
        }
    }

    private fun askForFileNameAndSave(htmlContent: String, onSaved: (() -> Unit)? = null) {
        val input = EditText(this@EditorActivity)
        val suggestedName = currentDocPath?.let { File(it).nameWithoutExtension } ?: ""
        input.setText(suggestedName)

        AlertDialog.Builder(this@EditorActivity)
            .setTitle("فائل کا نام دیں")
            .setView(input)
            .setPositiveButton("محفوظ کریں") { _, _ ->
                val fileName = input.text.toString()
                if (fileName.isNotEmpty()) {
                    val docsDir = File(filesDir, "docs")
                    if (!docsDir.exists()) docsDir.mkdirs()
                    val file = File(docsDir, "$fileName.html")
                    saveHtmlToFile(htmlContent, file.absolutePath, onSaved)
                } else {
                    Toast.makeText(this@EditorActivity, "فائل کا نام خالی نہیں ہوسکتا", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("منسوخ کریں", null)
            .show()
    }

    private fun saveHtmlToFile(htmlContent: String, path: String, onSaved: (() -> Unit)? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(path)
                FileOutputStream(file).use { out ->
                    out.write(htmlContent.toByteArray())
                }
                backupToExternalFolder(file.name, htmlContent)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, "فائل محفوظ ہوگئی", Toast.LENGTH_SHORT).show()
                    currentDocPath = file.absolutePath // Update current path
                    loadedHtml = htmlContent // Track the saved state
                    onSaved?.invoke()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, "فائل محفوظ نہیں ہوسکی: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveDocumentSilently() {
        val path = currentDocPath ?: return
        mEditor.evaluateJavascript("javascript:getDocumentHtml();") { html ->
            if (html != null && html != "null") {
                val unescapedHtml = unescapeJsonString(html)
                if (unescapedHtml.trim() != loadedHtml.trim()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val file = File(path)
                            FileOutputStream(file).use { out ->
                                out.write(unescapedHtml.toByteArray())
                            }
                            backupToExternalFolder(file.name, unescapedHtml)
                            loadedHtml = unescapedHtml
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun backupToExternalFolder(fileName: String, htmlContent: String) {
        try {
            val sharedPref = getSharedPreferences("UrduWriterPrefs", MODE_PRIVATE)
            val uriStr = sharedPref.getString("backup_folder_uri", null) ?: return
            val uri = android.net.Uri.parse(uriStr)
            val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
            if (pickedDir != null && pickedDir.canWrite()) {
                val existingFile = pickedDir.findFile(fileName)
                if (existingFile != null) {
                    existingFile.delete()
                }
                val newFile = pickedDir.createFile("text/html", fileName)
                if (newFile != null) {
                    contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        output.write(htmlContent.toByteArray())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkChangesAndExit() {
        mEditor.evaluateJavascript("javascript:getDocumentHtml();") { html ->
            val currentHtml = unescapeJsonString(html)

            if (currentHtml.trim() != loadedHtml.trim()) {
                AlertDialog.Builder(this@EditorActivity)
                    .setTitle("تبدیلیاں محفوظ کریں؟")
                    .setMessage("کیا آپ اس دستاویز میں کی گئی تبدیلیاں محفوظ کرنا چاہتے ہیں؟")
                    .setPositiveButton("محفوظ کریں") { dialog, _ ->
                        dialog.dismiss()
                        saveDocument(false) {
                            finish()
                        }
                    }
                    .setNegativeButton("محفوظ نہ کریں") { dialog, _ ->
                        dialog.dismiss()
                        isDiscardingChanges = true
                        finish()
                    }
                    .setNeutralButton("منسوخ کریں", null)
                    .show()
            } else {
                finish()
            }
        }
    }

    private fun saveAsPdf() {
        val sharedPref = getSharedPreferences("UrduWriterPrefs", MODE_PRIVATE)
        val pageSize = sharedPref.getString("pref_page_size", "A4") ?: "A4"
        val isLandscape = sharedPref.getBoolean("pref_page_landscape", false)

        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val jobName = "${getString(R.string.app_name)} Document"
        val printAdapter = getWrappedPrintAdapter(jobName)

        var mediaSize = when (pageSize) {
            "Letter" -> PrintAttributes.MediaSize.NA_LETTER
            "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
            "A3" -> PrintAttributes.MediaSize.ISO_A3
            "A5" -> PrintAttributes.MediaSize.ISO_A5
            "Executive" -> PrintAttributes.MediaSize("EXECUTIVE", "Executive", 7250, 10500)
            "Custom" -> {
                val customWidth = sharedPref.getFloat("pref_custom_page_width", 8.27f)
                val customHeight = sharedPref.getFloat("pref_custom_page_height", 11.69f)
                val widthMils = Math.round(customWidth * 1000).toInt()
                val heightMils = Math.round(customHeight * 1000).toInt()
                PrintAttributes.MediaSize("CUSTOM_SIZE", "Custom", widthMils, heightMils)
            }

            else -> PrintAttributes.MediaSize.ISO_A4
        }

        if (isLandscape) {
            mediaSize = mediaSize.asLandscape()
        }

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, printAdapter, printAttributes)
    }

    private val RC_CREATE_PDF = 1001

    private fun showPdfExportOptions() {
        val options = arrayOf(
            "براہ راست فائل میں ایکسپورٹ کریں (Direct Export)",
            "سسٹم پرنٹ ڈائیلاگ استعمال کریں (Print View)"
        )
        AlertDialog.Builder(this)
            .setTitle("پی ڈی ایف فارمیٹ میں محفوظ کریں")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startDirectPdfExport()
                    1 -> saveAsPdf()
                }
            }
            .show()
    }

    private fun startDirectPdfExport() {
        val suggestedName =
            currentDocPath?.let { File(it).nameWithoutExtension } ?: "UrduWriter_Document"
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_TITLE, "$suggestedName.pdf")
        }
        startActivityForResult(intent, RC_CREATE_PDF)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_CREATE_PDF && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            exportWebViewToPdfUri(uri)
        }
    }

    private fun exportWebViewToPdfUri(uri: android.net.Uri) {
        val sharedPref = getSharedPreferences("UrduWriterPrefs", MODE_PRIVATE)
        val pageSize = sharedPref.getString("pref_page_size", "A4") ?: "A4"
        val isLandscape = sharedPref.getBoolean("pref_page_landscape", false)

        val jobName = "${getString(R.string.app_name)} Document"
        val printAdapter = getWrappedPrintAdapter(jobName)

        var mediaSize = when (pageSize) {
            "Letter" -> PrintAttributes.MediaSize.NA_LETTER
            "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
            "A3" -> PrintAttributes.MediaSize.ISO_A3
            "A5" -> PrintAttributes.MediaSize.ISO_A5
            "Executive" -> PrintAttributes.MediaSize("EXECUTIVE", "Executive", 7250, 10500)
            "Custom" -> {
                val customWidth = sharedPref.getFloat("pref_custom_page_width", 8.27f)
                val customHeight = sharedPref.getFloat("pref_custom_page_height", 11.69f)
                val widthMils = Math.round(customWidth * 1000).toInt()
                val heightMils = Math.round(customHeight * 1000).toInt()
                PrintAttributes.MediaSize("CUSTOM_SIZE", "Custom", widthMils, heightMils)
            }

            else -> PrintAttributes.MediaSize.ISO_A4
        }

        if (isLandscape) {
            mediaSize = mediaSize.asLandscape()
        }

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        try {
            val pfd = contentResolver.openFileDescriptor(uri, "w")
            if (pfd == null) {
                Toast.makeText(this, "فائل بنانے میں ناکامی", Toast.LENGTH_SHORT).show()
                return
            }

            val progressDialog = AlertDialog.Builder(this)
                .setTitle("پی ڈی ایف فائل بن رہی ہے")
                .setMessage("براہ کرم انتظار کریں...")
                .setCancelable(false)
                .create()
            progressDialog.show()

            android.print.PdfPrinter.print(
                printAdapter,
                printAttributes,
                pfd,
                object : android.print.PdfPrinter.Callback {
                    override fun onSuccess() {
                        try {
                            pfd.close()
                        } catch (_: Exception) {
                        }

                        runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(
                                this@EditorActivity,
                                "پی ڈی ایف فائل براہ راست محفوظ ہو گئی ہے",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onFailure(error: String?) {
                        try {
                            pfd.close()
                        } catch (_: Exception) {
                        }

                        runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(
                                this@EditorActivity,
                                "ایکسپورٹ میں ناکامی: $error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                })

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "ایکسپورٹ میں خرابی: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getWrappedPrintAdapter(jobName: String): PrintDocumentAdapter {
        val originalAdapter = mEditor.createPrintDocumentAdapter(jobName)
        return object : PrintDocumentAdapter() {
            override fun onStart() {
                runOnUiThread {
                    mEditor.evaluateJavascript("javascript:preparePrint()", null)
                }
                originalAdapter.onStart()
            }

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                originalAdapter.onLayout(
                    oldAttributes,
                    newAttributes,
                    cancellationSignal,
                    callback,
                    extras
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                originalAdapter.onWrite(pages, destination, cancellationSignal, callback)
            }

            override fun onFinish() {
                originalAdapter.onFinish()
                runOnUiThread {
                    mEditor.evaluateJavascript("javascript:restorePrint()", null)
                }
            }
        }
    }

    // --- WebView/JavaScript Interop ---

    inner class WebAppInterface {
        @JavascriptInterface
        fun updateCurrentStyle(fontName: String, fontSize: String) {
            val primaryFont = fontName.split(',')[0].trim().replace("'", "").replace("\"", "")
            val sizeValue = fontSize.replace("px", "").toFloatOrNull()?.toInt()

            runOnUiThread {
                binding.fontSelectButton.text = primaryFont
                if (sizeValue != null) {
                    binding.fontSizeButton.text = sizeValue.toString()
                }
            }
        }
    }

    private fun execJs(func: String, arg: String? = null) {
        val script = if (arg != null) "javascript:$func('$arg');" else "javascript:$func();"
        mEditor.evaluateJavascript(script, null)
    }

    private fun execJs(func: String, arg1: String, arg2: String) {
        val script = "javascript:$func('$arg1', '$arg2');"
        mEditor.evaluateJavascript(script, null)
    }

    private fun injectAllFontsCSS() {
        lifecycleScope.launch(Dispatchers.IO) {
            val css = StringBuilder()

            try {
                val builtIn = assets.list("fonts")?.toList() ?: emptyList()
                builtIn.forEach {
                    val name = it.substringBeforeLast(".")
                    val cssFriendlyName = name.replace("'", "\'")
                    val url = "file:///android_asset/fonts/$it"
                    css.append("@font-face { font-family:'$cssFriendlyName'; src:url('$url'); }")
                }
            } catch (_: Exception) { /* Ignore */ }

            try {
                val userDir = File(filesDir, "user_fonts")
                if (userDir.exists()) {
                    val userFonts = userDir.listFiles()?.toList() ?: emptyList()
                    userFonts.forEach {
                        val name = it.nameWithoutExtension
                        val cssFriendlyName = name.replace("'", "\'")
                        val url = "file://${it.absolutePath}"
                        css.append("@font-face { font-family:'$cssFriendlyName'; src:url('$url'); }")
                    }
                }
            } catch (_: Exception) { /* Ignore */ }

            val js = """
                (function(){
                    var s=document.getElementById('fontCSS');
                    if(!s){
                        s=document.createElement('style');
                        s.id='fontCSS';
                        document.head.appendChild(s);
                    }
                    s.innerHTML = `$css`;
                })();
            """
            withContext(Dispatchers.Main) {
                mEditor.evaluateJavascript(js, null)
            }
        }
    }

    // --- Editor Actions ---

    private fun applyFont(name: String) {
        execJs("applyFont", name)
    }

    private fun applyFontSize(size: Int) {
        execJs("applyFontSize", size.toString())
    }

    private fun insertSymbolWithFont(symbol: String, fontName: String) {
        val escapedSymbol = symbol.replace("'", "\\'").replace("\"", "\\\"")
        val escapedFontName = fontName.replace("'", "\\'").replace("\"", "\\\"")
        execJs("insertSymbol", escapedSymbol, escapedFontName)
    }

    // --- Dialogs ---

    private fun showFontSelectionDialog() {
        if (allFonts.isEmpty()) loadFontList()
        
        val fontsArray = allFonts.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("فونٹ منتخب کریں")
            .setItems(fontsArray) { _, which ->
                val selectedFont = fontsArray[which]
                binding.fontSelectButton.text = selectedFont
                applyFont(selectedFont)
            }
            .show()
    }

    private fun showFontSizeDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        
        // Pre-fill current size
        val currentSize = binding.fontSizeButton.text.toString()
        input.setText(currentSize)
        input.setSelection(input.text.length)

        AlertDialog.Builder(this)
            .setTitle("فونٹ سائز منتخب کریں")
            .setMessage("سائز 8 اور 150 کے درمیان درج کریں۔")
            .setView(input)
            .setPositiveButton("ٹھیک ہے") { _, _ ->
                val size = input.text.toString().toIntOrNull()
                if (size != null && size in 8..150) {
                    binding.fontSizeButton.text = size.toString()
                    applyFontSize(size)
                } else {
                    Toast.makeText(this, "براہ کرم 8 اور 150 کے درمیان ایک نمبر درج کریں۔", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("منسوخ کریں", null)
            .show()
    }

    private fun showColorPickerDialog() {
        ColorPickerDialog
            .Builder(this)
            .setTitle("رنگ منتخب کریں")
            .setColorShape(ColorShape.SQAURE)
            .setDefaultColor(R.color.black)
            .setColorListener { _, colorHex ->
                execJs("applyFontColor", colorHex)
            }
            .show()
    }

    private fun showSymbolsDialog() {
        val dialog = SymbolDialogFragment()
        dialog.setOnSymbolSelectedListener { symbol, fontName ->
            insertSymbolWithFont(symbol, fontName)
        }
        dialog.show(supportFragmentManager, "SymbolDialogFragment")
    }

    private fun showBulletDialog() {
        val dialog = BulletDialogFragment()
        dialog.setOnBulletSelectedListener { bullet ->
            execJs("setListBullet", bullet)
        }
        dialog.show(supportFragmentManager, "BulletDialogFragment")
    }

    private fun showPageLayoutDialog() {
        val sharedPref = getSharedPreferences("UrduWriterPrefs", MODE_PRIVATE)
        val currentSize = sharedPref.getString("pref_page_size", "A4") ?: "A4"
        val currentMargin = sharedPref.getString("pref_page_margin", "0.75in") ?: "0.75in"
        val currentLandscape = sharedPref.getBoolean("pref_page_landscape", false)

        val dialogView = layoutInflater.inflate(R.layout.dialog_page_layout, null)

        val spinnerSize = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerPageSize)
        val spinnerMargin = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerPageMargin)
        val layoutCustomMargins =
            dialogView.findViewById<View>(R.id.layoutCustomMargins)

        val editMarginTop = dialogView.findViewById<EditText>(R.id.editMarginTop)
        val editMarginBottom =
            dialogView.findViewById<EditText>(R.id.editMarginBottom)
        val editMarginLeft = dialogView.findViewById<EditText>(R.id.editMarginLeft)
        val editMarginRight = dialogView.findViewById<EditText>(R.id.editMarginRight)

        val radioGroupOrientation =
            dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupOrientation)
        val radioPortrait = dialogView.findViewById<android.widget.RadioButton>(R.id.radioPortrait)
        val radioLandscape =
            dialogView.findViewById<android.widget.RadioButton>(R.id.radioLandscape)

        if (currentLandscape) {
            radioLandscape.isChecked = true
        } else {
            radioPortrait.isChecked = true
        }

        val editPageWidth = dialogView.findViewById<EditText>(R.id.editPageWidth)
        val editPageHeight = dialogView.findViewById<EditText>(R.id.editPageHeight)

        val customWidth = sharedPref.getFloat("pref_custom_page_width", 8.27f)
        val customHeight = sharedPref.getFloat("pref_custom_page_height", 11.69f)

        // Setup Spinners
        val sizeOptions = arrayOf("A4", "Letter", "Legal", "A3", "A5", "Executive", "Custom")
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sizeOptions)
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSize.adapter = sizeAdapter
        spinnerSize.setSelection(sizeOptions.indexOf(currentSize).coerceAtLeast(0))

        spinnerSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedSize = sizeOptions[position]
                when (selectedSize) {
                    "A4" -> {
                        editPageWidth.setText("8.27")
                        editPageHeight.setText("11.69")
                        editPageWidth.isEnabled = false
                        editPageHeight.isEnabled = false
                    }

                    "Letter" -> {
                        editPageWidth.setText("8.5")
                        editPageHeight.setText("11.0")
                        editPageWidth.isEnabled = false
                        editPageHeight.isEnabled = false
                    }

                    "Legal" -> {
                        editPageWidth.setText("8.5")
                        editPageHeight.setText("14.0")
                        editPageWidth.isEnabled = false
                        editPageHeight.isEnabled = false
                    }

                    "A3" -> {
                        editPageWidth.setText("11.69")
                        editPageHeight.setText("16.54")
                        editPageWidth.isEnabled = false
                        editPageHeight.isEnabled = false
                    }

                    "A5" -> {
                        editPageWidth.setText("5.83")
                        editPageHeight.setText("8.27")
                        editPageWidth.isEnabled = false
                        editPageHeight.isEnabled = false
                    }

                    "Executive" -> {
                        editPageWidth.setText("7.25")
                        editPageHeight.setText("10.5")
                        editPageWidth.isEnabled = false
                        editPageHeight.isEnabled = false
                    }

                    "Custom" -> {
                        editPageWidth.setText(customWidth.toString())
                        editPageHeight.setText(customHeight.toString())
                        editPageWidth.isEnabled = true
                        editPageHeight.isEnabled = true
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val marginOptions = arrayOf(
            "نارمل (0.75 انچ)",
            "تنگ (0.5 انچ)",
            "چوڑا (1.0 انچ)",
            "کوئی حاشیہ نہیں (0 انچ)",
            "اپنی مرضی کا (Custom)"
        )
        val marginValues = arrayOf("0.75in", "0.5in", "1.0in", "0in", "custom")
        val marginAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, marginOptions)
        marginAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMargin.adapter = marginAdapter

        // Check if currentMargin is custom
        val presetMarginIndex = marginValues.indexOf(currentMargin)
        if (presetMarginIndex != -1) {
            spinnerMargin.setSelection(presetMarginIndex)
            layoutCustomMargins.visibility = View.GONE
        } else {
            // It's custom! Set spinner selection to "custom"
            spinnerMargin.setSelection(marginValues.indexOf("custom"))
            layoutCustomMargins.visibility = View.VISIBLE

            // Parse custom margin value e.g. "0.75in 0.75in 0.75in 0.75in" (top right bottom left)
            val parts = currentMargin.split(" ")
            if (parts.size == 4) {
                editMarginTop.setText(parts[0].replace("in", ""))
                editMarginRight.setText(parts[1].replace("in", ""))
                editMarginBottom.setText(parts[2].replace("in", ""))
                editMarginLeft.setText(parts[3].replace("in", ""))
            }
        }

        // Show/hide custom margins layout dynamically based on selection
        spinnerMargin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (marginValues[position] == "custom") {
                    layoutCustomMargins.visibility = View.VISIBLE
                } else {
                    layoutCustomMargins.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle("صفحہ کی ترتیب (Page Layout)")
            .setView(dialogView)
            .setPositiveButton("ٹھیک ہے") { _, _ ->
                val selectedSize = sizeOptions[spinnerSize.selectedItemPosition]
                val selectedLandscape = radioLandscape.isChecked

                var selectedMargin = marginValues[spinnerMargin.selectedItemPosition]
                if (selectedMargin == "custom") {
                    val topVal = editMarginTop.text.toString().toDoubleOrNull() ?: 0.75
                    val rightVal = editMarginRight.text.toString().toDoubleOrNull() ?: 0.75
                    val bottomVal = editMarginBottom.text.toString().toDoubleOrNull() ?: 0.75
                    val leftVal = editMarginLeft.text.toString().toDoubleOrNull() ?: 0.75

                    selectedMargin = "${topVal}in ${rightVal}in ${bottomVal}in ${leftVal}in"
                }

                val finalWidth = editPageWidth.text.toString().toFloatOrNull() ?: 8.27f
                val finalHeight = editPageHeight.text.toString().toFloatOrNull() ?: 11.69f

                // Save to shared preferences
                sharedPref.edit().apply {
                    putString("pref_page_size", selectedSize)
                    putString("pref_page_margin", selectedMargin)
                    putBoolean("pref_show_guidelines", false)
                    putInt("pref_guideline_offset", 0)
                    putBoolean("pref_page_landscape", selectedLandscape)
                    putFloat("pref_custom_page_width", finalWidth)
                    putFloat("pref_custom_page_height", finalHeight)
                    apply()
                }

                // Apply to editor WebView
                mEditor.evaluateJavascript(
                    "javascript:setPageSettings('$selectedSize', '$selectedMargin', false, 0, $selectedLandscape, $finalWidth, $finalHeight);",
                    null
                )
                Toast.makeText(this, "صفحہ کی ترتیب لاگو ہو گئی", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("منسوخ کریں", null)
            .show()
    }

    override fun startActionMode(callback: android.view.ActionMode.Callback?): android.view.ActionMode? {
        return super.startActionMode(callback, android.view.ActionMode.TYPE_FLOATING)
    }

    override fun startActionMode(
        callback: android.view.ActionMode.Callback?,
        type: Int
    ): android.view.ActionMode? {
        return super.startActionMode(callback, android.view.ActionMode.TYPE_FLOATING)
    }
}
