package com.xfire.textlinker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.Date
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.xfire.textlinker.network.TextLinkerApiService
import com.xfire.textlinker.NoteEntity
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.ExecutorService

class ScanFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvScanHint: TextView
    private lateinit var btnScanQR: Button
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var apiService: TextLinkerApiService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val viewModel: NotesViewModel by activityViewModels()
    
    // Server URL
    private val serverUrl = "https://textlinker.pro"
    private val TAG = "ScanFragment"
    private val chunkSize = 8000 // default chunk size in characters
    private val NOTE_DELIM = "\n\n---TEXTLINKER NOTE---\n\n"
    private val COMBINED_HEADER = "// TextLinker Combined v1\n"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewFinder = view.findViewById(R.id.viewFinder)
        tvScanHint = view.findViewById(R.id.tvScanHint)
        btnScanQR = view.findViewById(R.id.btnScanQR)
        
        // Initialize API service
        apiService = TextLinkerApiService(serverUrl)

        cameraExecutor = Executors.newSingleThreadExecutor()
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
        
        // Set up button click listener
        btnScanQR.setOnClickListener {
            checkCameraPermission()
        }
        
        // Initial state - don't start camera automatically
        tvScanHint.text = "Press the button to scan a QR code"
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        // Extract token from QR code
                        val rawValue = barcode.rawValue ?: ""
                        val token = if (rawValue.contains("token=")) {
                            rawValue.substringAfter("token=", "")
                        } else {
                            // If no token= prefix, assume the entire value is the token
                            rawValue
                        }
                        
                        if (token.isNotEmpty()) {
                            // Stop camera preview once we have a token
                            stopCamera()

                            // Persist last scanned token and timestamp
                            activity?.runOnUiThread {
                                val prefs = requireContext().getSharedPreferences("textlinker_prefs", android.content.Context.MODE_PRIVATE)
                                val previousToken = prefs.getString("last_token", null)
                                Log.d(TAG, "Token scanned: previous='$previousToken' new='$token'")
                                
                                if (previousToken != null && previousToken != token) {
                                    // Clear last uploaded payload only if token actually changed
                                    prefs.edit()
                                        .remove("last_uploaded_payload_$previousToken")
                                        .remove("upload_in_progress_$previousToken")
                                        .apply()
                                    Log.d(TAG, "Token changed: cleared prefs for previous token")
                                }
                                
                                prefs.edit()
                                    .putString("last_token", token)
                                    .putLong("last_token_timestamp", System.currentTimeMillis())
                                    .apply()
                                Log.d(TAG, "Saved token and timestamp to prefs")
                                tvScanHint.text = "Token scanned: $token\nPreparing to upload notes..."
                            }

                            // Upload all local notes to the server using the token in a coroutine
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    uploadAllLocalNotes(token)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in fetchTextFromServer", e)
                                    requireActivity().runOnUiThread {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    activity?.runOnUiThread {
                        tvScanHint.text = "Error scanning QR code: ${e.message}"
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun stopCamera() {
        if (::cameraProvider.isInitialized) {
            cameraProvider.unbindAll()
        }
    }
    
    private suspend fun uploadAllLocalNotes(token: String) {
        try {
            Log.d(TAG, "Starting auto-upload of local notes after scan")

            val localNotes = viewModel.getLocalNotes()
            Log.d(TAG, "Retrieved ${localNotes.size} local notes")

            localNotes.forEachIndexed { index, note ->
                Log.d(TAG, "Local note #$index: id=${note.id}, title='${note.title.take(20)}...', fromServer=${note.fromServer}, contentLength=${note.content.length}")
            }

            if (localNotes.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No local notes to upload", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
                return
            }

            val combined = buildCombinedText(localNotes)
            val prefs = requireContext().getSharedPreferences("textlinker_prefs", android.content.Context.MODE_PRIVATE)

            // Flag upload in progress
            prefs.edit().putBoolean("upload_in_progress_$token", true).apply()

            withContext(Dispatchers.Main) {
                tvScanHint.text = "Uploading ${localNotes.size} notes (${combined.length} chars)..."
            }

            val success = if (combined.length <= chunkSize) {
                val (ok, code) = uploadTextAwait(token, combined)
                Log.d(TAG, "upload /upload done code=$code ok=$ok len=${combined.length}")
                ok
            } else {
                val totalChunks = ceil(combined.length.toDouble() / chunkSize.toDouble()).toInt()
                Log.d(TAG, "upload-chunk totalChunks=$totalChunks chunkSize=$chunkSize totalLen=${combined.length}")
                var allOk = true
                var idx = 0
                while (idx < totalChunks && allOk) {
                    val start = idx * chunkSize
                    val end = min(start + chunkSize, combined.length)
                    val part = combined.substring(start, end)
                    val (ok, code) = uploadChunkAwait(token, idx, totalChunks, part)
                    Log.d(TAG, "chunk $idx/$totalChunks code=$code ok=$ok len=${part.length}")
                    allOk = allOk && ok
                    idx++
                }
                allOk
            }

            // Clear in-progress flag
            prefs.edit().remove("upload_in_progress_$token").apply()

            if (success) {
                // Remember last uploaded payload to avoid echo on refresh
                prefs.edit()
                    .putString("last_uploaded_payload_${token}", combined)
                    .putLong("last_upload_success_${token}", System.currentTimeMillis())
                    .apply()

                // Mark notes so they won't be re-uploaded as local-only next time
                localNotes.forEach { note ->
                    val updated = note.copy(fromServer = true)
                    viewModel.update(updated)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Notes uploaded", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Upload failed", Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-upload error", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
        }
    }

    private fun buildCombinedText(notes: List<NoteEntity>): String {
        val sb = StringBuilder()
        sb.append(COMBINED_HEADER)
        notes.forEach { note ->
            sb.append("Title: ").append(note.title).append('\n')
            sb.append("Created: ").append(Date(note.timestamp)).append('\n')
            sb.append(NOTE_DELIM)
            sb.append(note.content)
            sb.append("\n\n")
        }
        return sb.toString()
    }

    private suspend fun uploadTextAwait(token: String, text: String): Pair<Boolean, Int> = suspendCancellableCoroutine { cont ->
        apiService.uploadText(token, text) { success, code, _ ->
            if (cont.isActive) cont.resume(success to code)
        }
    }

    private suspend fun uploadChunkAwait(token: String, idx: Int, total: Int, chunk: String): Pair<Boolean, Int> = suspendCancellableCoroutine { cont ->
        apiService.uploadChunk(token, idx, total, chunk) { ok, code, _ ->
            if (cont.isActive) cont.resume(ok to code)
        }
    }

    // Simple loading indicator hook to keep UI consistent
    private fun showLoading(isLoading: Boolean) {
        try {
            btnScanQR.isEnabled = !isLoading
        } catch (_: Exception) {}
    }

    // scheduleRetryUpload() should be called only if initial upload returned false
    private fun scheduleRetryUpload(token: String, notesText: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(3000)
            try {
                val (ok, _) = uploadTextAwait(token, notesText)
                if (ok) {
                    Log.d(TAG, "Delayed retry succeeded")
                } else {
                    Log.e(TAG, "Delayed retry failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during retry upload", e)
            }
        }
    }
    
    // Method removed as we're no longer saving received text to the database
    // Instead, we're uploading our saved notes to the server
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        imageAnalyzer = null
    }
}
