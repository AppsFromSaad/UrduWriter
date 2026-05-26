package com.sf.urduwriter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sf.urduwriter.databinding.ItemFontBinding
import java.io.File

class FontAdapter(
    private val fonts: MutableList<FontItem>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<FontAdapter.FontViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val binding = ItemFontBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FontViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        val font = fonts[position]
        holder.bind(font)
    }

    override fun getItemCount() = fonts.size

    fun updateData(newFonts: List<FontItem>) {
        fonts.clear()
        fonts.addAll(newFonts)
        notifyDataSetChanged()
    }

    inner class FontViewHolder(private val binding: ItemFontBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(font: FontItem) {
            binding.fontNameTextView.text = font.name
            
            // Apply font preview
            try {
                val typeface = if (font.name == "Default") {
                    android.graphics.Typeface.DEFAULT
                } else if (!font.isDeletable) {
                    android.graphics.Typeface.createFromAsset(binding.root.context.assets, "fonts/${font.name}")
                } else {
                    val userFontsDir = File(binding.root.context.filesDir, "user_fonts")
                    val fontFile = File(userFontsDir, font.name)
                    android.graphics.Typeface.createFromFile(fontFile)
                }
                binding.fontPreviewTextView.typeface = typeface
            } catch (e: Exception) {
                // Font load failed
            }

            if (font.isDeletable) {
                binding.deleteButton.visibility = View.VISIBLE
                binding.deleteButton.setOnClickListener { onDeleteClick(font.name) }
            } else {
                binding.deleteButton.visibility = View.GONE
            }
        }
    }
}
