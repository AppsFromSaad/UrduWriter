package com.sf.urduwriter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class DocumentAdapter(
    private val onDocumentClick: (Document) -> Unit,
    private val onDeleteClick: (Document) -> Unit,
    private val onShareClick: (Document) -> Unit
) : ListAdapter<Document, DocumentAdapter.DocumentViewHolder>(DocumentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val document = getItem(position)
        holder.bind(document)
    }

    inner class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.document_name)
        private val dateTextView: TextView = itemView.findViewById(R.id.document_date)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
        private val shareButton: ImageButton = itemView.findViewById(R.id.share_button)

        fun bind(document: Document) {
            nameTextView.text = document.name
            dateTextView.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(document.lastModified))
            itemView.setOnClickListener { onDocumentClick(document) }
            deleteButton.setOnClickListener { onDeleteClick(document) }
            shareButton.setOnClickListener { onShareClick(document) }
        }
    }
}

class DocumentDiffCallback : DiffUtil.ItemCallback<Document>() {
    override fun areItemsTheSame(oldItem: Document, newItem: Document): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: Document, newItem: Document): Boolean {
        return oldItem == newItem
    }
}
