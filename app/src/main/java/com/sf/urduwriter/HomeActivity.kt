package com.sf.urduwriter

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
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

        binding.newDocumentFab.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }

        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        loadDocuments()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_font_manager -> {
                startActivity(Intent(this, FontManagerActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
        val docFiles = docsDir.listFiles { file -> file.extension == "html" || file.extension == "docx" }?.toList() ?: emptyList()
        val documents = docFiles.map { 
            Document(it.nameWithoutExtension, it.absolutePath, it.lastModified())
        }
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