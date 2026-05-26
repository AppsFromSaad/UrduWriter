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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream

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

        setupEditor()
        setupToolbar()
        setupFileMenu()
        loadFontList()

        // Apply Nastaliq font to title and layout
        try {
            val jameelNooriFont = android.graphics.Typeface.createFromAsset(assets, "fonts/Jameel_noori_nastaleeq.ttf")
            binding.toolbarTitle.typeface = jameelNooriFont
            binding.fileButton.typeface = jameelNooriFont
        } catch (e: Exception) {}

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
    }

    private fun setupFileMenu() {
        binding.fileButton.setOnClickListener {
            val options = arrayOf(
                getString(R.string.save),
                "نئے نام سے محفوظ کریں",
                getString(R.string.save_as_pdf)
            )

            AlertDialog.Builder(this)
                .setTitle("فائل")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> saveDocument(false)
                        1 -> saveDocument(true) // Force new name
                        2 -> saveAsPdf()
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
                    val content = file.readText()
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

    private fun saveDocument(forceNewName: Boolean, onSaved: (() -> Unit)? = null) {
        mEditor.evaluateJavascript("javascript:getDocumentHtml();") { html ->
            val unescapedHtml = try {
                if (html == null || html == "null") "" else JSONTokener(html).nextValue() as String
            } catch (e: Exception) {
                if (html != null && html.length >= 2) {
                    html.substring(1, html.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\'", "'")
                        .replace("\\\\", "\\")
                } else {
                    html ?: ""
                }
            }

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
                val unescapedHtml = try {
                    JSONTokener(html).nextValue() as String
                } catch (e: Exception) {
                    html.substring(1, html.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\'", "'")
                        .replace("\\\\", "\\")
                }
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
            val sharedPref = getSharedPreferences("UrduWriterPrefs", android.content.Context.MODE_PRIVATE)
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
            val currentHtml = try {
                if (html == null || html == "null") "" else JSONTokener(html).nextValue() as String
            } catch (e: Exception) {
                ""
            }

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
        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val jobName = "${getString(R.string.app_name)} Document"
        val printAdapter: PrintDocumentAdapter = mEditor.createPrintDocumentAdapter(jobName)
        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, printAdapter, printAttributes)
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
}
