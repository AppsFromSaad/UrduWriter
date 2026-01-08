package com.sf.urduwriter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class DocumentAdapter(
    private val onDocumentClick: (Document) -> Unit,
    private val onDeleteClick: (Document) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

    private val documents = mutableListOf<Document>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val document = documents[position]
        holder.bind(document)
    }

    override fun getItemCount() = documents.size

    fun submitList(newDocuments: List<Document>) {
        documents.clear()
        documents.addAll(newDocuments.sortedByDescending { it.lastModified })
        notifyDataSetChanged()
    }

    inner class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.document_name)
        private val dateTextView: TextView = itemView.findViewById(R.id.document_date)
        private val deleteButton: Button = itemView.findViewById(R.id.delete_button)

        fun bind(document: Document) {
            nameTextView.text = document.name
            dateTextView.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(document.lastModified))
            itemView.setOnClickListener { onDocumentClick(document) }
            deleteButton.setOnClickListener { onDeleteClick(document) }
        }
    }
}