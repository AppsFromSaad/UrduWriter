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

class SymbolDialogFragment : DialogFragment() {

    private lateinit var binding: DialogSymbolsBinding
    private var listener: ((String, String) -> Unit)? = null
    private lateinit var recentSymbols: MutableList<Pair<String, String>>

    // Cache for loaded typefaces to improve performance
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
        binding = DialogSymbolsBinding.inflate(LayoutInflater.from(context))
        loadRecentSymbols()
        setupFontSpinner()
        setupSubsetSpinner()
        updateRecentSymbolsGrid()

        return AlertDialog.Builder(requireActivity())
            .setTitle("Symbols")
            .setView(binding.root)
            .setNegativeButton("Cancel", null)
            .create()
    }

    override fun onDestroy() {
        super.onDestroy()
        typefaceCache.clear()
    }

    private fun setupFontSpinner() {
        val fonts = mutableListOf("Default")
        val builtInFonts = FontManager.getBuiltInFonts(requireContext()).map { it.substringBeforeLast(".") }
        val userFonts = FontManager.getUserAddedFonts(requireContext()).map { it.substringBeforeLast(".") }
        fonts.addAll(builtInFonts)
        fonts.addAll(userFonts)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fonts.distinct())
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
        val subsetNames = mutableListOf("All")
        subsetNames.addAll(subsets.keys)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, subsetNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.subsetSpinner.adapter = adapter
        binding.subsetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSymbolGrid()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.subsetSpinner.setSelection(3) // Default to Arabic
    }

    private fun updateSymbolGrid() {
        val selectedFontName = binding.fontSpinner.selectedItem as String
        val selectedSubsetName = binding.subsetSpinner.selectedItem as String

        val typeface = getTypeface(selectedFontName)

        val symbols = if (selectedSubsetName == "All") {
            subsets.values.flatMap { it.map { charCode -> charCode.toChar().toString() } }
        } else {
            val range = subsets[selectedSubsetName]
            range?.map { it.toChar().toString() } ?: emptyList()
        }

        val adapter = SymbolGridAdapter(symbols.map { it to selectedFontName }, typeface)
        binding.symbolsGrid.adapter = adapter
        binding.symbolsGrid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val (symbol, fontName) = adapter.getItem(position) as Pair<String, String>
            addSymbolToRecents(symbol, fontName)
            listener?.invoke(symbol, fontName)
            dismiss()
        }
    }

    private fun updateRecentSymbolsGrid() {
        val adapter = SymbolGridAdapter(recentSymbols, null)
        binding.recentSymbolsGrid.adapter = adapter
        binding.recentSymbolsGrid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val (symbol, fontName) = recentSymbols[position]
            listener?.invoke(symbol, fontName)
            dismiss()
        }
    }

    private fun addSymbolToRecents(symbol: String, fontName: String) {
        val pair = symbol to fontName
        recentSymbols.remove(pair)
        recentSymbols.add(0, pair)
        if (recentSymbols.size > 10) { // Keep only 10 recent symbols
            recentSymbols.removeAt(10)
        }
        saveRecentSymbols()
    }

    private fun saveRecentSymbols() {
        val prefs = requireActivity().getSharedPreferences("UrduWriterPrefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(recentSymbols)
        prefs.edit().putString("recent_symbols", json).apply()
    }

    private fun loadRecentSymbols() {
        val prefs = requireActivity().getSharedPreferences("UrduWriterPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("recent_symbols", null)
        recentSymbols = if (json != null) {
            val type = object : TypeToken<MutableList<Pair<String, String>>>() {}.type
            Gson().fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    private fun getTypeface(fontName: String): Typeface {
        if (typefaceCache.containsKey(fontName)) {
            return typefaceCache[fontName] ?: Typeface.DEFAULT
        }

        val typeface: Typeface
        if (fontName == "Default") {
            typeface = Typeface.DEFAULT
        } else {
            var createdTypeface: Typeface? = null
            // Try user fonts first
            try {
                val userFontsDir = File(requireContext().filesDir, "user_fonts")
                val userFontFile = File(userFontsDir, "$fontName.ttf").takeIf { it.exists() }
                    ?: File(userFontsDir, "$fontName.otf").takeIf { it.exists() }
                if (userFontFile != null) {
                    createdTypeface = Typeface.createFromFile(userFontFile)
                }
            } catch (e: Exception) { /* Ignore */ }

            // If not found, try assets
            if (createdTypeface == null) {
                try {
                    createdTypeface = Typeface.createFromAsset(requireContext().assets, "fonts/$fontName.ttf")
                } catch (e: Exception) {
                    try {
                        createdTypeface = Typeface.createFromAsset(requireContext().assets, "fonts/$fontName.otf")
                    } catch (e2: Exception) { /* Font not found */ }
                }
            }
            typeface = createdTypeface ?: Typeface.DEFAULT
        }

        typefaceCache[fontName] = typeface
        return typeface
    }

    inner class SymbolGridAdapter(
        private val symbols: List<Pair<String, String>>,
        private val defaultTypeface: Typeface? // Used for the main grid where all symbols share one font
    ) : BaseAdapter() {
        override fun getCount(): Int = symbols.size
        override fun getItem(position: Int): Any = symbols[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val textView = if (convertView == null) {
                TextView(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(100, 100)
                    textSize = 24f
                    gravity = android.view.Gravity.CENTER
                }
            } else {
                convertView as TextView
            }
            val (symbol, fontName) = symbols[position]
            textView.text = symbol
            textView.typeface = defaultTypeface ?: getTypeface(fontName)
            return textView
        }
    }
}