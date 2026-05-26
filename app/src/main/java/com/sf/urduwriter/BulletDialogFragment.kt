package com.sf.urduwriter

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.gson.Gson
import com.sf.urduwriter.databinding.DialogBulletsBinding

class BulletDialogFragment : DialogFragment() {

    private var _binding: DialogBulletsBinding? = null
    private val binding get() = _binding!!
    
    private var listener: ((String) -> Unit)? = null
    private var bulletList: MutableList<String> = mutableListOf()

    fun setOnBulletSelectedListener(callback: (String) -> Unit) {
        this.listener = callback
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        _binding = DialogBulletsBinding.inflate(LayoutInflater.from(context))

        loadBullets()

        val adapter = BulletAdapter(context, bulletList)
        binding.bulletsGrid.adapter = adapter

        binding.bulletsGrid.setOnItemClickListener { _, _, position, _ ->
            if (position < bulletList.size) {
                listener?.invoke(bulletList[position])
                dismiss()
            }
        }

        binding.defineNewBulletButton.setOnClickListener {
            val symbolDialog = SymbolDialogFragment()
            symbolDialog.setOnSymbolSelectedListener { symbol, _ ->
                if (!bulletList.contains(symbol)) {
                    bulletList.add(symbol)
                    saveBullets()
                    adapter.notifyDataSetChanged()
                }
            }
            symbolDialog.show(parentFragmentManager, "SymbolDialogForBullets")
        }

        return AlertDialog.Builder(context)
            .setTitle("بلٹ منتخب کریں")
            .setView(binding.root)
            .setNegativeButton("منسوخ کریں", null)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun saveBullets() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("UrduWriterPrefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(bulletList.toTypedArray())
        prefs.edit().putString("custom_bullets", json).apply()
    }

    private fun loadBullets() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("UrduWriterPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("custom_bullets", null)
        
        bulletList = if (json != null) {
            try {
                val array = Gson().fromJson(json, Array<String>::class.java)
                array.toMutableList()
            } catch (e: Exception) {
                getDefaultBullets()
            }
        } else {
            getDefaultBullets()
        }
    }

    private fun getDefaultBullets(): MutableList<String> {
        return mutableListOf("•", "○", "■", "➢", "✓", "★", "♦", "→")
    }

    private class BulletAdapter(context: Context, objects: List<String>) : 
        ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, objects) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.gravity = Gravity.CENTER
            view.textSize = 24f
            view.setTextColor(Color.BLACK)
            return view
        }
    }
}
