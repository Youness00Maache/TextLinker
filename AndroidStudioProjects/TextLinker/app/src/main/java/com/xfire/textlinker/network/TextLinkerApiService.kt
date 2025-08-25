package com.xfire.textlinker.network

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service class for handling network communication with the TextLinker server.
 */
class TextLinkerApiService(private val serverUrl: String) {
    
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkLoggingInterceptor())
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val TAG = "TextLinkerApi"
    
    /**
     * Generate a token from the server for QR code generation
     * @param callback Callback to handle the response
     */
    fun generateToken(callback: (String?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url("$serverUrl/generate-token")
            .get()
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        val token = jsonObject.getString("token")
                        callback(token, null)
                    } else {
                        callback(null, IOException("Unexpected response: ${response.code}"))
                    }
                } catch (e: Exception) {
                    callback(null, e)
                }
            }
        })
    }

    /**
     * Dump recent network logs to Logcat for debugging
     */
    fun dumpRecentNetworkLogs(maxLines: Int = 300) {
        val lines = NetDebugLog.getLast(maxLines)
        Log.d(TAG, "--- Dumping last ${lines.size} network log lines ---")
        for (line in lines) {
            Log.d(TAG, line)
        }
        Log.d(TAG, "--- End of network log dump ---")
    }

    /**
     * Fetch raw response body from the server for /text/{token}.
     * Returns the body string even for non-200 codes; err is only set on network failure.
     */
    fun fetchTextRaw(token: String, callback: (Int, String?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url("$serverUrl/text/$token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "fetchTextRaw onFailure: ${e.message}")
                callback(-1, null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = try { response.body?.string() } catch (e: Exception) { e.message }
                Log.d(TAG, "fetchTextRaw onResponse: code=${response.code} body=${bodyStr?.take(200)}")
                callback(response.code, bodyStr, null)
            }
        })
    }

    /**
     * Prefer unread web-origin messages only: GET /text/{token}/unread-web
     */
    fun fetchUnreadWebRaw(token: String, callback: (Int, String?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url("$serverUrl/text/$token/unread-web")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "fetchUnreadWebRaw onFailure: ${e.message}")
                callback(-1, null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = try { response.body?.string() } catch (e: Exception) { e.message }
                Log.d(TAG, "fetchUnreadWebRaw onResponse: code=${response.code} body=${bodyStr?.take(200)}")
                callback(response.code, bodyStr, null)
            }
        })
    }

    /**
     * Upload a single chunk for large payloads to /upload-chunk endpoint
     */
    fun uploadChunk(
        token: String,
        chunkIndex: Int,
        totalChunks: Int,
        textChunk: String,
        callback: (Boolean, Int, String?) -> Unit
    ) {
        try {
            val maskedToken = if (token.length > 6) token.take(3) + "***" + token.takeLast(3) else "***"
            Log.d(TAG, "uploadChunk: token=$maskedToken idx=${chunkIndex + 1}/$totalChunks len=${textChunk.length}")
        } catch (_: Exception) {}

        val json = JSONObject().apply {
            put("token", token)
            put("chunkIndex", chunkIndex)
            put("totalChunks", totalChunks)
            put("textChunk", textChunk)
        }
        val body = json.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("$serverUrl/upload-chunk")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "uploadChunk onFailure idx=${chunkIndex + 1}: ${e.message}")
                callback(false, -1, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = try { response.body?.string() } catch (e: Exception) { e.message }
                Log.d(TAG, "uploadChunk onResponse idx=${chunkIndex + 1}: code=${response.code} body=${bodyStr}")
                callback(response.isSuccessful, response.code, bodyStr)
            }
        })
    }
    
    /**
     * Upload text to the server using a token
     * @param token The token to associate with the text
     * @param text The text to upload
     * @param callback Callback to handle the response
     */
    fun uploadText(token: String, text: String, callback: (Boolean, Int, String?) -> Unit) {
        try {
            val maskedToken = if (token.length > 6) token.take(3) + "***" + token.takeLast(3) else "***"
            Log.d(TAG, "uploadText: token=$maskedToken len=${text.length} prefix='${text.take(30)}'")
        } catch (_: Exception) {}

        val jsonObject = JSONObject()
        jsonObject.put("token", token)
        jsonObject.put("text", text)

        val requestBody = jsonObject.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("$serverUrl/upload")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "uploadText onFailure: ${e.message}")
                callback(false, -1, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = try { response.body?.string() } catch (e: Exception) { e.message }
                Log.d(TAG, "uploadText onResponse: code=${response.code} body=${bodyStr}")
                callback(response.isSuccessful, response.code, bodyStr)
            }
        })
    }

    // Backward-compatible wrapper used by existing code paths (e.g., ShareFragment)
    fun uploadText(token: String, text: String, legacyCallback: (Boolean, Exception?) -> Unit) {
        uploadText(token, text) { success, code, body ->
            if (success && code == 200) {
                legacyCallback(true, null)
            } else {
                legacyCallback(false, IOException("Unexpected response: code=$code body=${body ?: ""}"))
            }
        }
    }
    
    /**
     * Fetch unread web messages from the server using a token
     * @param token The token to retrieve messages for
     * @param callback Callback to handle the response (messages array, exception)
     */
    fun fetchUnreadWebMessages(token: String, callback: (List<WebMessage>?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url("$serverUrl/text/$token/unread-web")
            .get()
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "fetchUnreadWebMessages onFailure: ${e.message}")
                callback(null, e)
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "fetchUnreadWebMessages response: code=${response.code} body=${responseBody?.take(200)}")
                    
                    when (response.code) {
                        200 -> {
                            val jsonObject = JSONObject(responseBody)
                            if (jsonObject.has("messages")) {
                                val messagesArray = jsonObject.getJSONArray("messages")
                                val messages = mutableListOf<WebMessage>()
                                
                                for (i in 0 until messagesArray.length()) {
                                    val msgObj = messagesArray.getJSONObject(i)
                                    messages.add(WebMessage(
                                        id = msgObj.getString("id"),
                                        text = msgObj.getString("text"),
                                        createdAt = msgObj.getString("created_at")
                                    ))
                                }
                                
                                Log.d(TAG, "fetchUnreadWebMessages: received ${messages.size} messages")
                                callback(messages, null)
                            } else {
                                // Server returned old format - fallback detection
                                Log.w(TAG, "Server returned old format without 'messages' field")
                                callback(null, IOException("Server not providing unread messages; please update server."))
                            }
                        }
                        404 -> {
                            callback(emptyList(), null) // No messages found
                        }
                        else -> {
                            callback(null, IOException("Server error: ${response.code} - ${responseBody ?: "No response body"}"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchUnreadWebMessages parse error: ${e.message}")
                    callback(null, IOException("Failed to parse server response: ${e.message}"))
                }
            }
        })
    }

    /**
     * Fetch text from the server using a token (legacy endpoint for backward compatibility)
     * @param token The token to retrieve text for
     * @param callback Callback to handle the response
     */
    fun fetchText(token: String, callback: (String?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url("$serverUrl/text/$token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    when (response.code) {
                        200 -> {
                            val jsonObject = JSONObject(responseBody)
                            val text = jsonObject.getString("text")
                            callback(text, null)
                        }
                        404 -> {
                            callback(null, IOException("Text not found - the token may have expired (tokens expire after 10 minutes)"))
                        }
                        else -> {
                            callback(null, IOException("Server error: ${response.code} - ${responseBody ?: "No response body"}"))
                        }
                    }
                } catch (e: Exception) {
                    callback(null, IOException("Failed to parse server response: ${e.message}"))
                }
            }
        })
    }

    private inner class NetworkLoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            val path = url.encodedPath
            val method = request.method
            val bodyStr = requestBodyToString(request)
            val token = extractTokenFromRequest(url, bodyStr)
            val ts = isoTs()
            val outLine = "OUT HTTP $ts $method $path token=${token.take(6)} body=${bodyStr.take(800)}"
            Log.d(TAG, outLine)
            NetDebugLog.add(outLine)

            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                val errLine = "IN  HTTP $path code=ERR body=${e.message ?: ""}"
                Log.e(TAG, errLine)
                NetDebugLog.add(errLine)
                throw e
            }

            val responseBody = response.body
            val contentType = responseBody?.contentType()
            val bodyBytes = responseBody?.bytes() ?: ByteArray(0)
            val bodyPrefix = bytesToString(bodyBytes, 800)
            val tsIn = isoTs()
            val inLine = "IN  HTTP $tsIn $path code=${response.code} body=${bodyPrefix}"
            Log.d(TAG, inLine)
            NetDebugLog.add(inLine)

            val newBody = bodyBytes.toResponseBody(contentType)
            return response.newBuilder().body(newBody).build()
        }

        private fun requestBodyToString(request: Request): String {
            return try {
                val body = request.body ?: return ""
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readString(Charset.forName("UTF-8"))
            } catch (e: Exception) {
                ""
            }
        }

        private fun extractTokenFromRequest(url: HttpUrl, body: String): String {
            try {
                if (body.isNotEmpty()) {
                    val json = JSONObject(body)
                    if (json.has("token")) return json.getString("token")
                }
            } catch (_: Exception) {}
            val segments = url.pathSegments
            if (segments.size >= 2 && segments[0] == "text") {
                return segments[1]
            }
            return ""
        }

        private fun bytesToString(bytes: ByteArray, max: Int): String {
            val s = try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "" }
            return if (s.length > max) s.substring(0, max) else s
        }

        private fun isoTs(): String {
            return try {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                fmt.format(Date())
            } catch (_: Exception) { "" }
        }
    }

    data class WebMessage(
        val id: String,
        val text: String,
        val createdAt: String
    )
}

private object NetDebugLog {
    private const val MAX = 1000
    private val buf = ArrayList<String>(MAX)
    @Synchronized fun add(line: String) {
        if (buf.size >= MAX) buf.removeAt(0)
        buf.add(line)
    }
    @Synchronized fun getLast(n: Int): List<String> {
        val from = (buf.size - n).coerceAtLeast(0)
        return buf.subList(from, buf.size).toList()
    }
}