package com.example.impactalert_kotlin

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object HttpClient {
    private val client = OkHttpClient()

    suspend fun sendCrashAlert(serverUrl: String, lat: Double, lon: Double): Boolean {
        // Switch to a background thread to perform the network request
        return withContext(Dispatchers.IO) {
            try {
                val json = """{"status":"CRASH","lat":$lat,"lon":$lon}"""
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(serverUrl)
                    .post(body)
                    .build()

                // .execute() is a synchronous call, which is why we're in withContext(Dispatchers.IO)
                // .use {} ensures the response is closed automatically, preventing resource leaks
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("HttpClient", "Unsuccessful response: ${response.code} ${response.message}")
                    }
                    response.isSuccessful
                }
            } catch (e: IOException) {
                // This catches network errors (e.g., no connection, timeout)
                Log.e("HttpClient", "Network request failed", e)
                false
            } catch (e: Exception) {
                // This catches other potential errors, like an invalid URL
                Log.e("HttpClient", "Failed to send crash alert", e)
                false
            }
        }
    }
}
