package com.sf.urduwriter

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sf.urduwriter.databinding.DialogSymbolsBinding
import java.io.File

data class RecentSymbol(val symbol: String, val fontName: String)

class SymbolDialogFragment : DialogFragment() {

    private var _binding: DialogSymbolsBinding? = null
    private val binding get() = _binding!!

    private var listener: ((String, String) -> Unit)? = null
    private var recentSymbols: MutableList<RecentSymbol> = mutableListOf()

    private val typefaceCache = mutableMapOf<String, Typeface>()

    private val subsets = mapOf(
        "Basic Latin" to 0x0020..0x007F,
        "Latin-1 Supplement" to 0x00A0..0x00FF,
        "Arabic" to 0x0600..0x06FF,
        "Arabic Supplement" to 0x0750..0x077F,
        "Arabic Extended-A" to 0x08A0..0x08FF,
        "Arabic Presentation Forms-A" to 0xFB50..0xFDFF,
        "Arabic Presentation Forms-B" to 0xFE70..0xFEFF
    )

    fun setOnSymbolSelectedListener(listener: (String, String) -> Unit) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSymbolsBinding.inflate(LayoutInflater.from(context))
        
        loadRecentSymbols()
        setupFontSpinner()
        setupSubsetSpinner()
        updateRecentSymbolsGrid()

        return AlertDialog.Builder(requireContext())
            .setTitle("سمبلز (Symbols)")
            .setView(binding.root)
            .setNegativeButton("منسوخ کریں", null)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        typefaceCache.clear()
    }

    private fun setupFontSpinner() {
        val fonts = mutableListOf("Default")
        val context = context ?: return
        
        val builtInFonts = FontManager.getBuiltInFonts(context).map { it.substringBeforeLast(".") }
        val userFonts = FontManager.getUserAddedFonts(context).map { it.substringBeforeLast(".") }
        fonts.addAll(builtInFonts)
        fonts.addAll(userFonts)

        val distinctFonts = fonts.distinct()
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, distinctFonts)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.fontSpinner.adapter = adapter
        binding.fontSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSymbolGrid()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSubsetSpinner() {
        val context = context ?: return
        val subsetNames = mutableListOf("All")
        subsetNames.addAll(subsets.keys)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, subsetNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.subsetSpinner.adapter = adapter
        binding.subsetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSymbolGrid()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        if (subsetNames.size > 3) {
            binding.subsetSpinner.setSelection(3) // Default to Arabic if available
        }
    }

    private fun updateSymbolGrid() {
        val selectedFontName = binding.fontSpinner.selectedItem as? String ?: "Default"
        val selectedSubsetName = binding.subsetSpinner.selectedItem as? String ?: "All"

        val typeface = getTypeface(selectedFontName)

        val symbols = if (selectedSubsetName == "All") {
            subsets.values.flatMap { it.map { charCode -> charCode.toChar().toString() } }
        } else {
            val range = subsets[selectedSubsetName]
            range?.map { it.toChar().toString() } ?: emptyList()
        }

        val adapter = SymbolGridAdapter(symbols.map { RecentSymbol(it, selectedFontName) }, typeface)
        binding.symbolsGrid.adapter = adapter
        binding.symbolsGrid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            addSymbolToRecents(item.symbol, item.fontName)
            listener?.invoke(item.symbol, item.fontName)
            dismiss()
        }
    }

    private fun updateRecentSymbolsGrid() {
        val adapter = SymbolGridAdapter(recentSymbols, null)
        binding.recentSymbolsGrid.adapter = adapter
        binding.recentSymbolsGrid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position < recentSymbols.size) {
                val item = recentSymbols[position]
                listener?.invoke(item.symbol, item.fontName)
                dismiss()
            }
        }
    }

    private fun addSymbolToRecents(symbol: String, fontName: String) {
        val item = RecentSymbol(symbol, fontName)
        recentSymbols.remove(item)
        recentSymbols.add(0, item)
        if (recentSymbols.size > 12) { // Allow more recents
            recentSymbols.removeAt(recentSymbols.size - 1)
        }
        saveRecentSymbols()
    }

    private fun saveRecentSymbols() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("UrduWriterPrefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(recentSymbols)
        prefs.edit().putString("recent_symbols", json).apply()
    }

    private fun loadRecentSymbols() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("UrduWriterPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("recent_symbols", null)
        
        recentSymbols = mutableListOf()
        
        if (!json.isNullOrEmpty()) {
            try {
                // Primary format attempt
                val type = object : TypeToken<MutableList<RecentSymbol>>() {}.type
                val loaded: MutableList<RecentSymbol>? = Gson().fromJson(json, type)
                if (loaded != null) {
                    recentSymbols = loaded
                }
            } catch (e: Exception) {
                // Secondary check: If class names changed (inner to top-level), Gson might still recover 
                // but let's try a safer migration from JSON string if possible.
                try {
                    // Try to parse as raw JSON array and reconstruct
                    val array = com.google.gson.JsonParser.parseString(json).asJsonArray
                    array.forEach { 
                        val obj = it.asJsonObject
                        val sym = obj.get("symbol")?.asString ?: ""
                        val font = obj.get("fontName")?.asString ?: "Default"
                        if (sym.isNotEmpty()) {
                            recentSymbols.add(RecentSymbol(sym, font))
                        }
                    }
                    saveRecentSymbols() // Save in clean format
                } catch (e2: Exception) {
                    // Final fallback: try the old Pair format
                    try {
                        val pairType = object : TypeToken<List<Pair<String, String>>>() {}.type
                        val oldFormat: List<Pair<String, String>>? = Gson().fromJson(json, pairType)
                        oldFormat?.forEach { 
                            recentSymbols.add(RecentSymbol(it.first, it.second))
                        }
                        saveRecentSymbols()
                    } catch (e3: Exception) {
                        recentSymbols = mutableListOf()
                    }
                }
            }
        }
    }

    private fun getTypeface(fontName: String): Typeface {
        if (typefaceCache.containsKey(fontName)) {
            return typefaceCache[fontName] ?: Typeface.DEFAULT
        }

        val context = context ?: return Typeface.DEFAULT
        val typeface: Typeface
        if (fontName == "Default") {
            typeface = Typeface.DEFAULT
        } else {
            var createdTypeface: Typeface? = null
            try {
                val userFontsDir = File(context.filesDir, "user_fonts")
                val fontFile = userFontsDir.listFiles()?.find { it.nameWithoutExtension == fontName }
                if (fontFile != null && fontFile.exists()) {
                    createdTypeface = Typeface.createFromFile(fontFile)
                }
            } catch (e: Exception) {}

            if (createdTypeface == null) {
                try {
                    val assetFonts = context.assets.list("fonts") ?: emptyArray()
                    val assetFile = assetFonts.find { it.substringBeforeLast(".") == fontName }
                    if (assetFile != null) {
                        createdTypeface = Typeface.createFromAsset(context.assets, "fonts/$assetFile")
                    }
                } catch (e: Exception) {}
            }
            typeface = createdTypeface ?: Typeface.DEFAULT
        }

        typefaceCache[fontName] = typeface
        return typeface
    }

    inner class SymbolGridAdapter(
        private val symbols: List<RecentSymbol>,
        private val defaultTypeface: Typeface?
    ) : BaseAdapter() {
        override fun getCount(): Int = symbols.size
        override fun getItem(position: Int): RecentSymbol = symbols[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val context = parent?.context ?: context ?: return View(parent?.context)
            val textView = if (convertView == null) {
                val size48dp = (48 * context.resources.displayMetrics.density).toInt()
                TextView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(size48dp, size48dp)
                    textSize = 24f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.BLACK)
                }
            } else {
                convertView as TextView
            }
            
            if (position < symbols.size) {
                val item = symbols[position]
                textView.text = item.symbol
                textView.typeface = defaultTypeface ?: getTypeface(item.fontName)
            }
            return textView
        }
    }
}