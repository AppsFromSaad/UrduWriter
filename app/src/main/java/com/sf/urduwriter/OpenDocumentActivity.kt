package com.sf.urduwriter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.sf.urduwriter.databinding.ActivityOpenDocumentBinding
import java.io.File

class OpenDocumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOpenDocumentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOpenDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val docsDir = File(this.filesDir, "docs")
        val docFiles = docsDir.listFiles()?.map { it.name } ?: emptyList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, docFiles)
        binding.documentListView.adapter = adapter

        binding.documentListView.setOnItemClickListener { _, _, position, _ ->
            val selectedDocName = docFiles[position]
            val selectedDocPath = File(docsDir, selectedDocName).absolutePath

            val resultIntent = Intent().apply {
                putExtra(EditorActivity.EXTRA_DOC_PATH, selectedDocPath)
            }

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}
