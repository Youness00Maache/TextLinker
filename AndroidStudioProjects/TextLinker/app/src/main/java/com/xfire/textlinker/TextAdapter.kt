package com.xfire.textlinker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TextAdapter(
    private var items: List<NoteEntity>,
    private val onItemClick: (NoteEntity) -> Unit,
    private val onDeleteClick: (NoteEntity) -> Unit,
    private val onOptionsClick: (NoteEntity, View) -> Unit
) : RecyclerView.Adapter<TextAdapter.TextViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text_link, parent, false)
        return TextViewHolder(view)
    }

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        holder.bind(items[position], onItemClick, onDeleteClick, onOptionsClick)
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<NoteEntity>) {
        items = newList
        notifyDataSetChanged()
    }

    class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvItemTitle = itemView.findViewById<TextView>(R.id.tvItemTitle)
        private val btnOptions = itemView.findViewById<ImageButton>(R.id.btnOptions)
        private val cardView = itemView.findViewById<View>(R.id.cardView)

        fun bind(
            note: NoteEntity,
            onItemClick: (NoteEntity) -> Unit,
            onDeleteClick: (NoteEntity) -> Unit,
            onOptionsClick: (NoteEntity, View) -> Unit
        ) {
            // Set the title text
            tvItemTitle.text = note.title
            
            // Set click listener for the card view
            cardView.setOnClickListener {
                onItemClick(note)
            }
            
            // Set click listener for the options button
            btnOptions.setOnClickListener { view ->
                onOptionsClick(note, view)
            }
        }
    }
}