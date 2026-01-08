package com.sf.urduwriter

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.sf.urduwriter.databinding.DialogHeaderBinding

class HeaderDialogFragment : DialogFragment() {

    private lateinit var binding: DialogHeaderBinding
    private var listener: ((String) -> Unit)? = null

    fun setOnHeaderSelectedListener(listener: (String) -> Unit) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogHeaderBinding.inflate(LayoutInflater.from(context))

        binding.headerBlank.setOnClickListener {
            listener?.invoke("blank")
            dismiss()
        }

        binding.headerThreeColumns.setOnClickListener {
            listener?.invoke("three_columns")
            dismiss()
        }

        binding.removeHeader.setOnClickListener {
            listener?.invoke("remove")
            dismiss()
        }

        return AlertDialog.Builder(requireActivity())
            .setTitle("Select a Header")
            .setView(binding.root)
            .create()
    }
}