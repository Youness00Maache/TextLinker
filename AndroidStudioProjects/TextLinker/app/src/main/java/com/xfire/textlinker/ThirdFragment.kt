package com.xfire.textlinker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs

class ThirdFragment : Fragment() {

    private lateinit var etTitle: EditText
    private lateinit var etContent: EditText
    private lateinit var btnSave: Button

    // Get the shared ViewModel (which must include an update method)
    private val viewModel: NotesViewModel by activityViewModels()
    // Retrieve arguments using Safe Args
    private val args: ThirdFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment (make sure fragment_third.xml is properly set up)
        return inflater.inflate(R.layout.fragment_third, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etTitle = view.findViewById(R.id.etTitle)
        etContent = view.findViewById(R.id.etContent)
        btnSave = view.findViewById(R.id.btnSave)

        // Prepopulate the fields if editing an existing note (noteId != 0)
        if (args.noteId != 0) {
            etTitle.setText(args.title)
            etContent.setText(args.content)
        }

        btnSave.setOnClickListener {
            val newTitle = etTitle.text.toString().trim()
            val newContent = etContent.text.toString().trim()
            if (newTitle.isNotEmpty() && newContent.isNotEmpty()) {
                if (args.noteId != 0) {
                    // Update the existing note
                    viewModel.update(NoteEntity(
                        id = args.noteId, 
                        title = newTitle, 
                        content = newContent,
                        timestamp = System.currentTimeMillis(),
                        fromServer = false
                    ))
                } else {
                    // Insert a new note
                    viewModel.insert(NoteEntity(
                        title = newTitle, 
                        content = newContent,
                        timestamp = System.currentTimeMillis(),
                        fromServer = false
                    ))
                }
                // Navigate back after saving
                findNavController().navigateUp()
            } else {
                if (newTitle.isEmpty()) etTitle.error = "Title required"
                if (newContent.isEmpty()) etContent.error = "Content required"
            }
        }
    }
}
