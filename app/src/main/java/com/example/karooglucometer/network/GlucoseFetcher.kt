/*
This file contains the class that acts to fetch the glucose data from the phone via HTTP
requests using BLE PAN.
 */

package com.example.karooglucometer.network

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.karooglucometer.data.GlucoseDao
import com.example.karooglucometer.data.GlucoseDatabase
import com.example.karooglucometer.data.GlucoseReading
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GlucoseFetcher(context: Context) {
    private val TAG = "GlucoseFetcher"
    
    // Configure OkHttp with aggressive timeouts for Bluetooth PAN
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)      // Connection timeout
        .readTimeout(5, TimeUnit.SECONDS)         // Read timeout
        .writeTimeout(5, TimeUnit.SECONDS)        // Write timeout
        .callTimeout(10, TimeUnit.SECONDS)        // Overall call timeout
        .retryOnConnectionFailure(true)           // Retry on failure
        .build()
        
    private val db: GlucoseDatabase = Room.databaseBuilder(
        context.applicationContext,
        GlucoseDatabase::class.java,
        "glucose_db"
    ).build()
    private val dao: GlucoseDao = db.glucoseDao()

    /**
     * Fetch glucose data from xDrip and save to database
     * This is a blocking call that should be run on IO dispatcher
     * @throws IOException if network request fails
     * @throws JSONException if response parsing fails
     */
    suspend fun fetchAndSave(phoneIp: String) {
        val url = "http://$phoneIp:17580/sgv.json"
        
        Log.d(TAG, "Attempting to fetch from: $url")
        
        try {
            // Build the request
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            // Execute synchronously (we're already on IO dispatcher from MainActivity)
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            
            // Check if response was successful
            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                Log.e(TAG, errorMsg)
                throw IOException(errorMsg)
            }
            
            // Get response body
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                throw IOException("Empty response from xDrip")
            }
            
            Log.d(TAG, "Received response: ${responseBody.take(200)}...")
            
            // Parse JSON response
            val glucoseValue = parseXDripResponse(responseBody)
            
            // Create and save reading
            val reading = GlucoseReading(
                timestamp = System.currentTimeMillis(),
                glucoseValue = glucoseValue
            )
            
            withContext(Dispatchers.IO) {
                dao.insert(reading)
            }
            
            Log.d(TAG, "Successfully saved glucose reading: $glucoseValue mg/dL")
            
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection timeout to $phoneIp - is xDrip web service running?")
            throw IOException("Connection timeout - check xDrip web service is enabled", e)
        } catch (e: ConnectException) {
            Log.e(TAG, "Connection refused to $phoneIp - wrong IP or port?")
            throw IOException("Connection refused - verify phone IP ($phoneIp) and xDrip port (17580)", e)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host: $phoneIp - check IP address")
            throw IOException("Invalid IP address: $phoneIp", e)
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}", e)
            throw e
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error: ${e.message}", e)
            throw IOException("Failed to parse xDrip response", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            throw IOException("Unexpected error: ${e.message}", e)
        }
    }
    
    /**
     * Parse xDrip JSON response and extract glucose value
     * Expected format: [{"sgv": 120, "date": 1234567890, ...}]
     */
    private fun parseXDripResponse(json: String): Int {
        try {
            // xDrip returns an array with the most recent reading first
            val jsonArray = JSONArray(json)
            
            if (jsonArray.length() == 0) {
                throw JSONException("Empty array in xDrip response")
            }
            
            val firstReading = jsonArray.getJSONObject(0)
            
            // Get the glucose value (sgv = Sensor Glucose Value)
            if (!firstReading.has("sgv")) {
                throw JSONException("Missing 'sgv' field in xDrip response")
            }
            
            val glucoseValue = firstReading.getInt("sgv")
            
            // Sanity check the value
            if (glucoseValue < 20 || glucoseValue > 600) {
                Log.w(TAG, "Suspicious glucose value: $glucoseValue mg/dL (outside normal range)")
            }
            
            return glucoseValue
            
        } catch (e: JSONException) {
            // Fallback to primitive parsing if JSON parsing fails
            Log.w(TAG, "JSON parsing failed, trying primitive parsing", e)
            return parsePrimitive(json)
        }
    }
    
    /**
     * Fallback primitive parsing (original method)
     */
    private fun parsePrimitive(json: String): Int {
        val value = json.substringAfter("\"sgv\":", "")
            .substringBefore(",", "")
            .trim()
            .toIntOrNull()
            
        if (value == null) {
            throw JSONException("Could not extract 'sgv' value from response")
        }
        
        return value
    }
}
