package com.xfire.textlinker

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.xfire.textlinker.network.TextLinkerApiService

class FirstFragment : Fragment() {

    private lateinit var searchEditText: EditText
    private lateinit var tvEmpty: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var fabQrScanner: FloatingActionButton
    private lateinit var fabHelp: FloatingActionButton
    private lateinit var adapter: TextLinkAdapter
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private val serverUrl = "https://textlinker.pro"
    private val apiService: TextLinkerApiService by lazy { TextLinkerApiService(serverUrl) }
    private val NOTE_DELIM = "\n\n---TEXTLINKER NOTE---\n\n"
    private val COMBINED_HEADER = "// TextLinker Combined v1\n"

    // Shared ViewModel backed by Room
    private val viewModel: NotesViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchEditText = view.findViewById(R.id.etSearch)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        recyclerView = view.findViewById(R.id.rvItems)
        fabAdd = view.findViewById(R.id.fabAdd)
        fabQrScanner = view.findViewById(R.id.fabQrScanner)
        fabHelp = view.findViewById(R.id.fabHelp)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh)

        // Initialize adapter with the new FAB item layout.
        adapter = TextLinkAdapter(
            listOf(),
            { selectedNote ->
                // Navigate to the editor (ThirdFragment) with note details for editing.
                val action = FirstFragmentDirections.actionFirstFragmentToThirdFragment(
                    title = selectedNote.title,
                    content = selectedNote.content,
                    noteId = selectedNote.id
                )
                findNavController().navigate(action)
            },
            { noteToDelete ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Note")
                    .setMessage("Are you sure you want to delete this note?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.delete(noteToDelete)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            { noteToShare ->
                // Set the selected note in the ViewModel
                viewModel.setSelectedNote(noteToShare)
                // Navigate to the ShareFragment with arguments
                val action = FirstFragmentDirections.actionFirstFragmentToShareFragment(
                    noteId = noteToShare.id,
                    noteTitle = noteToShare.title,
                    noteContent = noteToShare.content
                )
                findNavController().navigate(action)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefreshLayout?.setOnRefreshListener {
            refreshFromServer()
        }

        viewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            if (notes.isEmpty()) {
                tvEmpty.text = "Any text you save will be displayed here"
                tvEmpty.visibility = View.VISIBLE
            } else {
                tvEmpty.visibility = View.GONE
            }
            updateFilteredList(searchEditText.text.toString(), notes)
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                val notes = viewModel.allNotes.value ?: listOf()
                val filtered = if (query.isEmpty()) {
                    notes
                } else {
                    notes.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.content.contains(query, ignoreCase = true)
                    }
                }
                adapter.updateList(filtered)
                tvEmpty.visibility = if (query.isNotEmpty() && filtered.isEmpty()) View.VISIBLE else View.GONE
                if (query.isNotEmpty() && filtered.isEmpty()) {
                    tvEmpty.text = "No matching text"
                } else if (notes.isEmpty()) {
                    tvEmpty.text = "Any text you save will be displayed here"
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        fabAdd.setOnClickListener {
            val action = FirstFragmentDirections.actionFirstFragmentToThirdFragment("", "", 0)
            findNavController().navigate(action)
        }

        fabQrScanner.setOnClickListener {
            findNavController().navigate(R.id.action_firstFragment_to_scanFragment)
        }
        
        fabHelp.setOnClickListener {
            findNavController().navigate(R.id.action_firstFragment_to_helpFragment)
        }
        // Developer: long-press Help to dump recent network logs
        fabHelp.setOnLongClickListener {
            try {
                apiService.dumpRecentNetworkLogs(300)
                Toast.makeText(requireContext(), "Dumped last 300 network logs to Logcat", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
            true
        }
    }

    private fun refreshFromServer() {
        val token = getLastToken()
        if (token.isNullOrEmpty()) {
            swipeRefreshLayout?.isRefreshing = false
            Toast.makeText(context, "No token available. Scan a QR code first.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if upload is in progress for this token
        val prefs = requireContext().getSharedPreferences("textlinker_prefs", Context.MODE_PRIVATE)
        val uploadInProgress = prefs.getBoolean("upload_in_progress_$token", false)
        if (uploadInProgress) {
            swipeRefreshLayout?.isRefreshing = false
            Toast.makeText(context, "Upload in progress. Try again in a sec.", Toast.LENGTH_SHORT).show()
            Log.d("FirstFragment", "Refresh blocked: upload_in_progress_$token = true")
            return
        }

        // Post-upload cooldown: avoid fetching for 10 seconds after upload success
        val lastUploadTs = prefs.getLong("last_upload_success_$token", 0L)
        if (lastUploadTs > 0 && System.currentTimeMillis() - lastUploadTs < 10_000) {
            swipeRefreshLayout?.isRefreshing = false
            Log.d("FirstFragment", "Refresh skipped due to post-upload cooldown for token=${token.take(6)}")
            Toast.makeText(context, "Please wait a moment after upload before refreshing.", Toast.LENGTH_SHORT).show()
            return
        }

        // Prefer unread web-origin messages only
        Log.d("FirstFragment", "OUT HTTP GET /text/$token/unread-web")

        apiService.fetchUnreadWebRaw(token) { code, rawBody, err ->
            activity?.runOnUiThread {
                swipeRefreshLayout?.isRefreshing = false
                Log.d("FirstFragment", "IN HTTP /text/$token/unread-web code=$code bodyPrefix=${rawBody?.take(400)}")

                if (err != null) {
                    Log.e("FirstFragment", "Refresh error: ${err.message}")
                    Toast.makeText(context, "Fetch error", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                if (code == 404) {
                    // Fallback to generic endpoint
                    Log.d("FirstFragment", "OUT HTTP GET /text/$token")
                    apiService.fetchTextRaw(token) { code2, body2, err2 ->
                        activity?.runOnUiThread {
                            Log.d("FirstFragment", "IN HTTP /text/$token code=$code2 bodyPrefix=${body2?.take(400)}")
                            if (err2 != null || body2 == null) {
                                Toast.makeText(context, "Fetch error", Toast.LENGTH_SHORT).show()
                                return@runOnUiThread
                            }
                            handleServerBody(token, body2)
                        }
                    }
                    return@runOnUiThread
                }

                if (rawBody == null) {
                    Toast.makeText(context, "Fetch error", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                handleServerBody(token, rawBody)
            }
        }
    }

    private fun handleServerBody(token: String, rawBody: String) {
        try {
            val json = org.json.JSONObject(rawBody)
            val prefs = requireContext().getSharedPreferences("textlinker_prefs", Context.MODE_PRIVATE)
            val lastUploaded = prefs.getString("last_uploaded_payload_" + token, null)

            if (json.has("messages")) {
                val arr = json.getJSONArray("messages")
                if (arr.length() > 50) {
                    Log.e("FirstFragment", "REFRESH SKIPPED unexpectedShape token=${token.take(6)} reason=messages>50 len=${arr.length()}")
                    Toast.makeText(context, "Server returned unexpected data; check logs", Toast.LENGTH_SHORT).show()
                    return
                }
                var candidate: String? = null
                // newest-first: iterate from end to start
                for (i in arr.length() - 1 downTo 0) {
                    val t = arr.optJSONObject(i)?.optString("text", null)
                    if (!t.isNullOrEmpty() && t.length < 200_000 && t != lastUploaded) {
                        candidate = if (t.contains(NOTE_DELIM) || t.startsWith(COMBINED_HEADER) || t.count { ch -> ch == '\n' } > 200) extractCandidateFromCombined(t) else t
                        break
                    }
                }
                val delimCount = candidate?.let { countDelims(it) } ?: 0
                Log.d("FirstFragment", "PARSE: messagesArr=${arr.length()} candidateLen=${candidate?.length ?: -1} delimCount=$delimCount")
                if (candidate == null) {
                    Log.d("FirstFragment", "REFRESH SKIPPED ECHO token=${token.take(6)}")
                    Toast.makeText(context, "No new text", Toast.LENGTH_SHORT).show()
                    return
                }
                handleFetchedText(token, candidate)
            } else if (json.has("text")) {
                val rawText = json.getString("text")
                val candidate = if (rawText.contains(NOTE_DELIM) || rawText.startsWith(COMBINED_HEADER) || rawText.count { it == '\n' } > 200) extractCandidateFromCombined(rawText) else rawText
                val delimCount = countDelims(rawText)
                Log.d("FirstFragment", "PARSE: messagesArr=0 candidateLen=${candidate.length} delimCount=$delimCount")
                val last = getLastUploadedPayloadForToken(token)
                if (candidate == last) {
                    Log.d("FirstFragment", "REFRESH SKIPPED ECHO token=${token.take(6)}")
                    Toast.makeText(context, "No new text", Toast.LENGTH_SHORT).show()
                    return
                }
                if (candidate.length >= 200_000) {
                    Log.e("FirstFragment", "REFRESH SKIPPED unexpectedShape token=${token.take(6)} reason=textTooLarge len=${candidate.length}")
                    Toast.makeText(context, "Server returned unexpected data; check logs", Toast.LENGTH_SHORT).show()
                    return
                }
                handleFetchedText(token, candidate)
            } else {
                Log.e("FirstFragment", "REFRESH SKIPPED unexpectedShape token=${token.take(6)} reason=no text/messages")
                Toast.makeText(context, "Server returned unexpected data; check logs", Toast.LENGTH_SHORT).show()
            }
        } catch (e: org.json.JSONException) {
            Log.e("FirstFragment", "REFRESH SKIPPED unexpectedShape token=${token.take(6)} reason=non-json prefix=${rawBody.take(120)}")
            Toast.makeText(context, "Server response unexpected — see logs", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleFetchedText(token: String, text: String) {
        val prefs = requireContext().getSharedPreferences("textlinker_prefs", Context.MODE_PRIVATE)
        val last = prefs.getString("last_uploaded_payload_" + token, null)
        if (text.length >= 200_000) {
            Log.e("FirstFragment", "REFRESH unexpected: text too large len=${text.length}")
            Toast.makeText(context, "Server returned unexpected data; check logs.", Toast.LENGTH_SHORT).show()
            return
        }
        if (text == last) {
            Log.d("FirstFragment", "REFRESH SKIPPED ECHO token=${token.take(6)}")
            Toast.makeText(context, "No new text", Toast.LENGTH_SHORT).show()
            return
        }
        saveReceivedTextAsNote(text)
        Log.d("FirstFragment", "REFRESH SAVED token=${token.take(6)} savedLen=${text.length}")
        Toast.makeText(context, "Received 1 new message", Toast.LENGTH_SHORT).show()
    }

    private fun countDelims(text: String): Int {
        val body = if (text.startsWith(COMBINED_HEADER)) text.removePrefix(COMBINED_HEADER) else text
        return body.split(NOTE_DELIM).size - 1
    }

    private fun extractCandidateFromCombined(text: String): String {
        val body = if (text.startsWith(COMBINED_HEADER)) text.removePrefix(COMBINED_HEADER) else text
        val parts = body.split(NOTE_DELIM)
        if (parts.size >= 2) return parts.last().trim()
        val paragraphs = body.split(Regex("\n{2,}"))
        return paragraphs.last().trim()
    }

    private fun getLastToken(): String? {
        val prefs = requireContext().getSharedPreferences("textlinker_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_token", null)
    }

    private fun getLastTokenTimestamp(): Long {
        val prefs = requireContext().getSharedPreferences("textlinker_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("last_token_timestamp", 0L)
    }

    private fun getLastUploadedPayloadForToken(token: String): String? {
        val prefs = requireContext().getSharedPreferences("textlinker_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_uploaded_payload_" + token, null)
    }

    private fun saveReceivedTextAsNote(text: String) {
        val lines = text.lines()
        val firstLine = lines.firstOrNull()?.trim().orEmpty()
        val title = if (firstLine.isNotEmpty() && firstLine.length <= 50) firstLine else "Text from Website"
        val note = NoteEntity(
            id = 0, // Let Room auto-generate the ID
            title = title, 
            content = text, 
            timestamp = System.currentTimeMillis(),
            fromServer = true
        )
        viewModel.insert(note)
        val token = getLastToken()?.take(6) ?: ""
        Log.d("FirstFragment", "Saved received note token=$token fromServer=true len=${text.length}")
    }

    private fun updateFilteredList(query: String, notes: List<NoteEntity>) {
        val filtered = if (query.isEmpty()) {
            notes
        } else {
            notes.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        }
        adapter.updateList(filtered)
        tvEmpty.visibility = if (notes.isEmpty() || (query.isNotEmpty() && filtered.isEmpty())) View.VISIBLE else View.GONE
        if (notes.isEmpty()) {
            tvEmpty.text = "Any text you save will be displayed here"
        } else if (query.isNotEmpty() && filtered.isEmpty()) {
            tvEmpty.text = "No matching text"
        }
    }
}
