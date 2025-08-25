package com.xfire.textlinker

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val noteDao = NoteDatabase.getDatabase(application).noteDao()
    val allNotes: LiveData<List<NoteEntity>> = noteDao.getAllNotes()
    
    // Selected note for sharing
    private val _selectedNote = MutableLiveData<NoteEntity?>()
    val selectedNote: LiveData<NoteEntity?> = _selectedNote
    
    fun setSelectedNote(note: NoteEntity) {
        _selectedNote.value = note
    }
    
    fun clearSelectedNote() {
        _selectedNote.value = null
    }

    fun insert(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Log before insertion
                Log.d("NotesViewModel", "Inserting note - title: '${note.title.take(20)}...', fromServer: ${note.fromServer}")
                
                // Ensure fromServer is properly set
                val newNote = note.copy(fromServer = note.fromServer)
                noteDao.insertNote(newNote)
                
                // Log after successful insertion
                Log.d("NotesViewModel", "Successfully inserted note - title: '${note.title.take(20)}...', fromServer: ${note.fromServer}")
                
                // Verify the note was inserted correctly
                val allNotes = noteDao.getAllNotes()
                Log.d("NotesViewModel", "Total notes in DB: ${allNotes.value?.size ?: 0}")
                
            } catch (e: Exception) {
                Log.e("NotesViewModel", "Error inserting note", e)
                throw e
            }
        }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            noteDao.deleteNote(note)
        }
    }

    fun update(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            noteDao.updateNote(note)
        }
    }

    suspend fun getLocalNotes(): List<NoteEntity> {
        return try {
            Log.d("NotesViewModel", "Fetching local notes...")
            val notes = noteDao.getLocalNotes()
            Log.d("NotesViewModel", "getLocalNotes() returned ${notes.size} notes")
            
            // Log all local notes for debugging
            notes.forEachIndexed { index, note ->
                Log.d("NotesViewModel", "Local note #$index: id=${note.id}, " +
                    "title='${note.title.take(20)}...', " +
                    "fromServer=${note.fromServer}, " +
                    "contentLength=${note.content.length}")
            }
            
            // Also log all notes for comparison
            val allNotes = noteDao.getAllNotes().value ?: emptyList()
            Log.d("NotesViewModel", "Total notes in database: ${allNotes.size}")
            allNotes.forEachIndexed { index, note ->
                Log.d("NotesViewModel", "All note #$index: id=${note.id}, " +
                    "title='${note.title.take(20)}...', " +
                    "fromServer=${note.fromServer}, " +
                    "contentLength=${note.content.length}")
            }
            
            notes
        } catch (e: Exception) {
            Log.e("NotesViewModel", "Error in getLocalNotes()", e)
            emptyList()
        }
    }
}
