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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.dhaval2404.colorpicker.ColorPickerDialog
import com.github.dhaval2404.colorpicker.model.ColorShape
import com.sf.urduwriter.databinding.ActivityEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOC_PATH = "extra_doc_path"
    }

    private lateinit var binding: ActivityEditorBinding
    private lateinit var mEditor: WebView
    private var currentDocPath: String? = null

    // --- Lifecycle and Setup ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEditor()
        setupToolbar()
        setupFileMenu()
        setupFontSpinner()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupEditor() {
        mEditor = binding.editor
        mEditor.settings.javaScriptEnabled = true
        mEditor.settings.allowFileAccess = true
        mEditor.addJavascriptInterface(WebAppInterface(), "Android") // Communication bridge
        mEditor.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        mEditor.textDirection = View.TEXT_DIRECTION_RTL
        mEditor.settings.useWideViewPort = false

        // Enable zoom controls
        mEditor.settings.setSupportZoom(true)
        mEditor.settings.builtInZoomControls = true
        mEditor.settings.displayZoomControls = false // Hide the on-screen zoom buttons

        mEditor.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectAllFontsCSS()
                // Set the default font size in the editor's javascript
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

        binding.bulletsButton.setOnClickListener { execJs("execCmd", "insertUnorderedList") }
        binding.bulletsButton.setOnLongClickListener {
            showBulletDialog()
            true
        }
        binding.numbersButton.setOnClickListener { execJs("execCmd", "insertOrderedList") }

        binding.symbolsButton.setOnClickListener { showSymbolsDialog() }
        binding.colorPickerButton.setOnClickListener { showColorPickerDialog() }
        binding.fontSizeButton.setOnClickListener { showFontSizeDialog() }
    }

    private fun setupFileMenu() {
        binding.fileButton.setOnClickListener {
            val options = arrayOf(
                getString(R.string.new_document),
                getString(R.string.save),
                "Save As", // New Option
                getString(R.string.save_as_pdf)
            )

            AlertDialog.Builder(this)
                .setTitle("فائل")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            currentDocPath = null // Reset path for new document
                            execJs("clearAll")
                        }
                        1 -> saveDocument(false)
                        2 -> saveDocument(true) // Force new name
                        3 -> saveAsPdf()
                    }
                }
                .show()
        }
    }

    private fun setupFontSpinner() {
        lifecycleScope.launch(Dispatchers.IO) {
            val builtIn = assets.list("fonts")?.map { it.substringBefore(".") } ?: emptyList()
            val userFontsDir = File(filesDir, "user_fonts")
            val userFonts = userFontsDir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()

            val allFonts = (builtIn + userFonts).toMutableList()

            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(this@EditorActivity, android.R.layout.simple_spinner_item, allFonts)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.fontSpinner.adapter = adapter

                binding.fontSpinner.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                            val font = parent.getItemAtPosition(pos) as String
                            applyFont(font)
                        }
                        override fun onNothingSelected(p0: AdapterView<*>) {}
                    }
            }
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
                    withContext(Dispatchers.Main) {
                        val escapedContent = content.replace("'", "\'").replace("\"", "\\\"").replace("\n", "<br>")
                        execJs("setDocumentHtml", escapedContent)
                    }
                }
            }
        } else {
            execJs("setDocumentHtml", "")
        }
    }

    private fun saveDocument(forceNewName: Boolean) {
        mEditor.evaluateJavascript("javascript:getDocumentHtml();") { html ->
            val unescapedHtml = html.substring(1, html.length - 1) // Remove surrounding quotes
                .replace("\\\"", "\"")
                .replace("\'", "'")

            if (currentDocPath != null && !forceNewName) {
                // Save to existing file
                saveHtmlToFile(unescapedHtml, currentDocPath!!)
            } else {
                // Ask for a new name
                askForFileNameAndSave(unescapedHtml)
            }
        }
    }

    private fun askForFileNameAndSave(htmlContent: String) {
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
                    saveHtmlToFile(htmlContent, file.absolutePath)
                } else {
                    Toast.makeText(this@EditorActivity, "فائل کا نام خالی نہیں ہوسکتا", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("منسوخ کریں", null)
            .show()
    }

    private fun saveHtmlToFile(htmlContent: String, path: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(path)
                FileOutputStream(file).use { out ->
                    out.write(htmlContent.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, "فائل محفوظ ہوگئی", Toast.LENGTH_SHORT).show()
                    currentDocPath = file.absolutePath // Update current path
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, "فائل محفوظ نہیں ہوسکی: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
                (binding.fontSpinner.adapter as? ArrayAdapter<String>)?.let { adapter ->
                    val position = adapter.getPosition(primaryFont)
                    if (position >= 0 && binding.fontSpinner.selectedItemPosition != position) {
                        binding.fontSpinner.setSelection(position)
                    }
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
                    val name = it.substringBefore(".")
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
        val escapedSymbol = symbol.replace("'", "\'")
        val escapedFontName = fontName.replace("'", "\'")
        execJs("insertSymbol", escapedSymbol, escapedFontName)
    }

    // --- Dialogs ---

    private fun showFontSizeDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("فونٹ سائز منتخب کریں")
            .setMessage("سائز 8 اور 150 کے درمیان درج کریں۔")
            .setView(input)
            .setPositiveButton("ٹھیک ہے") { _, _ ->
                val size = input.text.toString().toIntOrNull()
                if (size != null && size in 8..150) {
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
