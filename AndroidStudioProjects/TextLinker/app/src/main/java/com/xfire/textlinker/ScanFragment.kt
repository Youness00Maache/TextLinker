package com.xfire.textlinker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
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
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
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
import java.util.concurrent.ExecutorService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
                                    fetchTextFromServer(token)
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
    
    private suspend fun fetchTextFromServer(token: String) {
        try {
            Log.d("ScanFragment", "Starting upload of local notes to server")
            
            // Get all local notes
            val localNotes = viewModel.getLocalNotes()
            Log.d("ScanFragment", "Retrieved ${localNotes.size} local notes")
            
            // Log details of all local notes
            localNotes.forEachIndexed { index, note ->
                Log.d("ScanFragment", "Local note #$index: id=${note.id}, " +
                    "title='${note.title.take(20)}...', " +
                    "fromServer=${note.fromServer}, " +
                    "contentLength=${note.content.length}")
            }
            
            if (localNotes.isEmpty()) {
                Log.d("ScanFragment", "No local notes to upload")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No local notes to upload", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
                return
            }
            
            // Build combined text from local notes with a simple header and delimiter
            val combinedText = StringBuilder()
            combinedText.append(COMBINED_HEADER)
            localNotes.forEachIndexed { index, note ->
                if (index > 0) combinedText.append(NOTE_DELIM)
                combinedText.append(note.content)
            }
            val combinedTextStr = combinedText.toString()
            Log.d("ScanFragment", "Combined text length: ${combinedTextStr.length} characters")
            Log.d("ScanFragment", "Preview of combined text:\n${combinedTextStr.take(500)}...")
            
            // Update UI and set upload flag
            val prefs = requireContext().getSharedPreferences("textlinker_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("upload_in_progress_$token", true).apply()
            withContext(Dispatchers.Main) {
                tvScanHint.text = "Uploading text (${combinedTextStr.length} chars)..."
            }
            
            // Upload the combined text
            Log.d("ScanFragment", "Uploading text to server...")
            apiService.uploadText(token, combinedTextStr) { success, code, body ->
                try {
                    prefs.edit().remove("upload_in_progress_$token").apply()
                    if (success && code == 200) {
                        Log.d("ScanFragment", "Upload successful")
                        // Record last uploaded payload and timestamp for echo-skip and cooldown
                        prefs.edit()
                            .putString("last_uploaded_payload_$token", combinedTextStr)
                            .putLong("last_upload_success_$token", System.currentTimeMillis())
                            .apply()
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Upload successful!", Toast.LENGTH_SHORT).show()
                            showLoading(false)
                            tvScanHint.text = "Upload complete"
                        }
                    } else {
                        Log.e("ScanFragment", "Upload failed: code=$code body=${body ?: ""}")
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Upload failed: $code", Toast.LENGTH_SHORT).show()
                            showLoading(false)
                            tvScanHint.text = "Upload failed"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScanFragment", "Error handling upload callback", e)
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        showLoading(false)
                        tvScanHint.text = "Error during upload"
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("ScanFragment", "Error in fetchTextFromServer", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
        }
    }

    // Upload function with progress tracking and proper error handling
    private suspend fun uploadAllNotesToServer(token: String, notesText: String, notesCount: Int? = null, idsMasked: String? = null): Boolean {
        if (token.isBlank()) {
            Log.e(TAG, "uploadAllNotesToServer: token empty, abort")
            return false
        }
        val maskedToken = if (token.length > 6) token.take(3) + "***" + token.takeLast(3) else "***"
        Log.d(TAG, "uploadAllNotesToServer: token=$maskedToken len=${notesText.length} prefix='${notesText.take(30)}'")
        val countForLog = notesCount ?: -1
        val idsForLog = idsMasked ?: ""
        Log.d(TAG, "UPLOAD ATTEMPT token=${token.take(6)} count=$countForLog ids=$idsForLog")
        val prefs = requireContext().getSharedPreferences("textlinker_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("upload_in_progress_$token", true).apply()
        Log.d(TAG, "Set upload_in_progress_$token = true")
        withContext(Dispatchers.Main) {
            tvScanHint.text = "Uploading text (${notesText.length} chars)..."
        }
        val result = try {
            suspendCancellableCoroutine<Boolean> { cont ->
                apiService.uploadText(token, notesText) { success, code, body ->
                    Log.d(TAG, "uploadText callback success=$success code=$code bodyPrefix=${body?.take(120)}")
                    cont.resume(success)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadAllNotesToServer", e)
            false
        } finally {
            prefs.edit().remove("upload_in_progress_$token").apply()
            Log.d(TAG, "Cleared upload_in_progress_$token")
        }
        withContext(Dispatchers.Main) {
            if (result) {
                Log.d(TAG, "Upload success token=${token.take(6)} len=${notesText.length}")
                tvScanHint.text = "Upload succeeded"
                Toast.makeText(context, "Notes shared successfully", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Upload failed")
                tvScanHint.text = "Upload failed"
                Toast.makeText(context, "Failed to share notes", Toast.LENGTH_LONG).show()
            }
        }
        return result
    }

    // scheduleRetryUpload() should be called only if initial upload returned false
    private fun scheduleRetryUpload(token: String, notesText: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(3000)
            try {
                val success = uploadAllNotesToServer(token, notesText)
                if (success) {
                    Log.d(TAG, "Delayed retry succeeded; prefs handled in upload path")
                } else {
                    Log.e(TAG, "Delayed retry failed; will not overwrite prefs")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during retry upload", e)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        activity?.runOnUiThread {
            btnScanQR.isEnabled = !isLoading
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
