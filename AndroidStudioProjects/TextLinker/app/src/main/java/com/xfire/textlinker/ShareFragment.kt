package com.xfire.textlinker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.xfire.textlinker.network.TextLinkerApiService
import com.xfire.textlinker.util.QRCodeGenerator

class ShareFragment : Fragment() {
    
    private lateinit var tvShareInstructions: TextView
    private lateinit var ivQrCode: ImageView
    private lateinit var btnGenerateQR: Button
    
    // Get arguments from navigation
    private val args by navArgs<ShareFragmentArgs>()
    
    // Shared ViewModel
    private val viewModel: NotesViewModel by activityViewModels()
    
    // API service for server communication
    private lateinit var apiService: TextLinkerApiService
    
    // Server URL
    private val serverUrl = "https://textlinker.pro"
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_share, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        tvShareInstructions = view.findViewById(R.id.tvShareInstructions)
        ivQrCode = view.findViewById(R.id.ivQrCode)
        btnGenerateQR = view.findViewById(R.id.btnGenerateQR)
        
        // Initialize API service
        apiService = TextLinkerApiService(serverUrl)
        
        // Set up button click listener
        btnGenerateQR.setOnClickListener {
            generateQRCode()
        }
        
        // Check if we have arguments passed from navigation
        if (args.noteId > 0 || args.noteTitle.isNotEmpty()) {
            // Create a note entity from the arguments if not already in ViewModel
            if (viewModel.selectedNote.value == null) {
                val noteFromArgs = NoteEntity(
                    id = args.noteId,
                    title = args.noteTitle,
                    content = args.noteContent,
                    timestamp = System.currentTimeMillis(),
                    fromServer = false
                )
                viewModel.setSelectedNote(noteFromArgs)
            }
            
            // Update UI with note title
            tvShareInstructions.text = "Share note: ${args.noteTitle}"
        }
        
        // Also observe the selected note from ViewModel as a backup
        viewModel.selectedNote.observe(viewLifecycleOwner) { note ->
            if (note != null) {
                tvShareInstructions.text = "Share note: ${note.title}"
            }
        }
    }
    
    private fun generateQRCode() {
        // Disable button while generating
        btnGenerateQR.isEnabled = false
        btnGenerateQR.text = "Generating..."
        
        // Get the note to share
        val noteToShare = viewModel.selectedNote.value
        
        if (noteToShare == null) {
            Toast.makeText(context, "No note selected to share", Toast.LENGTH_SHORT).show()
            btnGenerateQR.isEnabled = true
            btnGenerateQR.text = "Generate QR Code"
            return
        }
        
        // Generate token from server
        apiService.generateToken { token, error ->
            activity?.runOnUiThread {
                if (token != null) {
                    // Upload the note content to the server with this token
                    uploadNoteToServer(token, noteToShare)
                } else {
                    Toast.makeText(context, "Error generating token: ${error?.message ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    btnGenerateQR.isEnabled = true
                    btnGenerateQR.text = "Generate QR Code"
                }
            }
        }
    }
    
    private fun uploadNoteToServer(token: String, note: NoteEntity) {
        // Combine title and content for sharing
        val textToShare = "${note.title}\n${note.content}"
        
        // Upload to server
        apiService.uploadText(token, textToShare) { success, error ->
            activity?.runOnUiThread {
                if (success) {
                    // Generate and display QR code
                    displayQRCode(token)
                } else {
                    Toast.makeText(context, "Error uploading text: ${error?.message ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                    btnGenerateQR.isEnabled = true
                    btnGenerateQR.text = "Generate QR Code"
                }
            }
        }
    }
    
    private fun displayQRCode(token: String) {
        // Generate QR code bitmap
        val qrBitmap = QRCodeGenerator.generateQRCode(token)
        
        if (qrBitmap != null) {
            // Display the QR code
            ivQrCode.setImageBitmap(qrBitmap)
            ivQrCode.visibility = View.VISIBLE
            
            // Update UI
            tvShareInstructions.text = "Scan this QR code to receive the shared note"
            btnGenerateQR.text = "Generate New QR Code"
            btnGenerateQR.isEnabled = true
        } else {
            Toast.makeText(context, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
            btnGenerateQR.text = "Try Again"
            btnGenerateQR.isEnabled = true
        }
    }
}