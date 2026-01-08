package com.sf.urduwriter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
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
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
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
        setupFontSizeSpinner()
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
                loadDocument()
            }
        }

        mEditor.loadUrl("file:///android_asset/editor/index_html_editor.html")
    }

    private fun setupToolbar() {
        binding.boldButton.setOnClickListener { execJs("applyBold") }
        binding.italicButton.setOnClickListener { execJs("applyItalic") }
        binding.underlineButton.setOnClickListener { execJs("applyUnderline") }
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
    }

    private fun setupFileMenu() {
        binding.fileButton.setOnClickListener {
            val options = arrayOf(
                "نیا ڈاکومنٹ",
                "محفوظ کریں",
                "پی ڈی ایف میں محفوظ کریں"
            )

            AlertDialog.Builder(this)
                .setTitle("فائل")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> execJs("clearAll")
                        1 -> saveDocument()
                        2 -> saveAsPdf()
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

    private fun setupFontSizeSpinner() {
        val fontSizes = (12..78).toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontSizes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.fontSizeSpinner.adapter = adapter

        binding.fontSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val size = parent.getItemAtPosition(position) as Int
                applyFontSize(size)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // --- Document Handling ---

    private fun loadDocument() {
        currentDocPath = intent.getStringExtra(EXTRA_DOC_PATH)
        val path = currentDocPath
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                if (file.extension.equals("docx", ignoreCase = true)) {
                    loadDocx(file)
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val content = file.readText()
                        withContext(Dispatchers.Main) {
                            val escapedContent = content.replace("'", "\'").replace("\"", "\\\"").replace("\n", "<br>")
                            execJs("setDocumentHtml", escapedContent)
                        }
                    }
                }
            }
        } else {
            execJs("setDocumentHtml", "")
        }
    }

    private fun loadDocx(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                FileInputStream(file).use { fis ->
                    val document = XWPFDocument(fis)
                    val text = document.paragraphs.joinToString("<br>") { it.text }
                    withContext(Dispatchers.Main) {
                        val escapedContent = text.replace("'", "\'").replace("\"", "\\\"")
                        execJs("setDocumentHtml", escapedContent)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, "DOCX فائل نہیں کھل سکی: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveDocument() {
        mEditor.evaluateJavascript("javascript:getDocumentHtml();") { html ->
            val unescapedHtml = html.substring(1, html.length - 1) // Remove surrounding quotes
                .replace("\\\"", "\"")
                .replace("\'", "'")
            saveAsDocx(unescapedHtml)
        }
    }

    private fun saveAsDocx(htmlContent: String) {
        val text = android.text.Html.fromHtml(htmlContent, android.text.Html.FROM_HTML_MODE_LEGACY).toString()

        val input = EditText(this@EditorActivity)
        val suggestedName = currentDocPath?.let { File(it).nameWithoutExtension } ?: ""
        input.setText(suggestedName)
        AlertDialog.Builder(this@EditorActivity)
            .setTitle("DOCX فائل کا نام دیں")
            .setView(input)
            .setPositiveButton("محفوظ کریں") { _, _ ->
                val fileName = input.text.toString()
                if (fileName.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val document = XWPFDocument()
                        val paragraph = document.createParagraph()
                        val run = paragraph.createRun()
                        run.setText(text)

                        val docsDir = File(filesDir, "docs")
                        if (!docsDir.exists()) docsDir.mkdirs()
                        val file = File(docsDir, "$fileName.docx")
                        try {
                            FileOutputStream(file).use { out ->
                                document.write(out)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@EditorActivity, "DOCX فائل محفوظ ہوگئی", Toast.LENGTH_SHORT).show()
                                currentDocPath = file.absolutePath
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@EditorActivity, "فائل محفوظ نہیں ہوسکی: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this@EditorActivity, "فائل کا نام خالی نہیں ہوسکتا", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("منسوخ کریں", null)
            .show()
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

                sizeValue?.let { size ->
                    (binding.fontSizeSpinner.adapter as? ArrayAdapter<Int>)?.let { adapter ->
                        val position = adapter.getPosition(size)
                        if (position >= 0 && binding.fontSizeSpinner.selectedItemPosition != position) {
                            binding.fontSizeSpinner.setSelection(position)
                        }
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
