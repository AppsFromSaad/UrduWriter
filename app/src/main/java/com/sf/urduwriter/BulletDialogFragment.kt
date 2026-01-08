package com.sf.urduwriter

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sf.urduwriter.databinding.DialogBulletsBinding

class BulletDialogFragment : DialogFragment() {

    private lateinit var binding: DialogBulletsBinding
    private var listener: ((String) -> Unit)? = null
    private lateinit var bulletList: MutableList<String>

    fun setOnBulletSelectedListener(listener: (String) -> Unit) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogBulletsBinding.inflate(LayoutInflater.from(context))

        loadBullets()

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, bulletList)
        binding.bulletsGrid.adapter = adapter

        binding.bulletsGrid.setOnItemClickListener { _, _, position, _ ->
            listener?.invoke(bulletList[position])
            dismiss()
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

        return AlertDialog.Builder(requireActivity())
            .setTitle("Select a Bullet")
            .setView(binding.root)
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun saveBullets() {
        val prefs = requireActivity().getSharedPreferences("UrduWriterPrefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(bulletList)
        prefs.edit().putString("custom_bullets", json).apply()
    }

    private fun loadBullets() {
        val prefs = requireActivity().getSharedPreferences("UrduWriterPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("custom_bullets", null)
        bulletList = if (json != null) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            Gson().fromJson(json, type)
        } else {
            // Default bullets
            mutableListOf("•", "◦", "▪", "➢", "✓", "→", "–")
        }
    }
}