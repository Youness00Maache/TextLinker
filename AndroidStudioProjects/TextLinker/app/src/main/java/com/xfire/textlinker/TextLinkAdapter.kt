package com.xfire.textlinker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import android.widget.PopupMenu

class TextLinkAdapter(
    private var items: List<NoteEntity>,
    private val onItemClick: (NoteEntity) -> Unit,
    private val onDeleteClick: (NoteEntity) -> Unit,
    private val onShareClick: (NoteEntity) -> Unit
) : RecyclerView.Adapter<TextLinkAdapter.NoteFabViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteFabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note_fab, parent, false)
        return NoteFabViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteFabViewHolder, position: Int) {
        holder.bind(items[position], onItemClick, onDeleteClick, onShareClick)
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<NoteEntity>) {
        items = newList
        notifyDataSetChanged()
    }

    class NoteFabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Import ExtendedFloatingActionButton from Material Components.
        private val noteFab = itemView.findViewById<ExtendedFloatingActionButton>(R.id.efabNote)

        fun bind(
            note: NoteEntity,
            onItemClick: (NoteEntity) -> Unit,
            onDeleteClick: (NoteEntity) -> Unit,
            onShareClick: (NoteEntity) -> Unit
        ) {
            // Set the FAB text to the note title.
            noteFab.text = note.title
            // When the FAB is clicked, trigger the onItemClick callback.
            noteFab.setOnClickListener {
                onItemClick(note)
            }
            // Set up popup menu for long press
            noteFab.setOnLongClickListener { view ->
                val popupMenu = PopupMenu(view.context, view)
                popupMenu.inflate(R.menu.note_options_menu)
                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_delete -> {
                            onDeleteClick(note)
                            true
                        }
                        R.id.action_share -> {
                            onShareClick(note)
                            true
                        }
                        else -> false
                    }
                }
                popupMenu.show()
                true
            }
        }
    }
}
