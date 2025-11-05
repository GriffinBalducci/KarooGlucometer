package com.example.karooglucometer.deprecated

import android.util.Log
import com.example.karooglucometer.data.GlucoseReading
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// These match what your Flask server sends
data class CGM(
    val glucose: String = "0",
    val timestamp: String = ""
)

data class GlucoseResponse(
    val cgm: CGM = CGM()
)

class HttpGlucoseDataSource(
    private val client: OkHttpClient,
    private var serverUrl: String = "http://192.168.4.34:5000/glucose"
) {
    
    suspend fun getLatestReading(): GlucoseReading? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(serverUrl).build()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body.string()
                    body.let { jsonData ->
                        val data = Gson().fromJson(jsonData, GlucoseResponse::class.java)
                        val glucose = data.cgm.glucose.toDoubleOrNull() ?: return@withContext null

                        GlucoseReading(
                            glucoseValue = glucose.toInt(),
                            timestamp = System.currentTimeMillis() // Use current time since server timestamp might be weird
                        )
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.d("HttpSource", "HTTP request failed: ${e.message}")
                null
            }
        }
    }
    
    suspend fun isAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(serverUrl).build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
}
