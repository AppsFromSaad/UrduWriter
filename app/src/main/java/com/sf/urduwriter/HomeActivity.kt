package com.sf.urduwriter

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sf.urduwriter.databinding.ActivityHomeBinding
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var docAdapter: DocumentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Urdu Writer"

        // Set custom font
        try {
            val jameelNooriFont = Typeface.createFromAsset(assets, "fonts/Jameel_noori_nastaleeq.ttf")
            binding.newDocumentButton.typeface = jameelNooriFont
            binding.fontManagerButton.typeface = jameelNooriFont
            binding.recentDocumentsLabel.typeface = jameelNooriFont
        } catch (e: Exception) {
            // Font not found, but the app will continue to work
        }

        binding.newDocumentButton.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }

        binding.fontManagerButton.setOnClickListener {
            startActivity(Intent(this, FontManagerActivity::class.java))
        }

        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        loadDocuments()
    }

    private fun setupRecyclerView() {
        docAdapter = DocumentAdapter(this::onDocumentClicked, this::onDeleteClicked)
        binding.documentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = docAdapter
        }
    }

    private fun loadDocuments() {
        val docsDir = File(filesDir, "docs")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }
        val docFiles = docsDir.listFiles { file -> file.extension == "html" }?.toList() ?: emptyList()
        val documents = docFiles.map {
            Document(it.nameWithoutExtension, it.absolutePath, it.lastModified())
        }.sortedByDescending { it.lastModified }
        docAdapter.submitList(documents)
    }

    private fun onDocumentClicked(document: Document) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_DOC_PATH, document.path)
        }
        startActivity(intent)
    }

    private fun onDeleteClicked(document: Document) {
        val file = File(document.path)
        file.delete()
        loadDocuments()
    }
}