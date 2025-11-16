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
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Data class for xDrip+ response with timestamp checking
 */
data class XDripReading(
    val glucoseValue: Int,
    val timestamp: Long,
    val isNew: Boolean
)

/**
 * GlucoseFetcher - Handles HTTP requests to xDrip+ web service
 * Optimized to only save new data (5-minute optimization)
 */
class GlucoseFetcher(context: Context) {
    private val TAG = "GlucoseFetcher"
    private val client: OkHttpClient
    private val dao: GlucoseDao

    init {
        // Initialize HTTP client with reasonable timeouts for BLE PAN
        client = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Initialize database
        val db = Room.databaseBuilder(
            context.applicationContext,
            GlucoseDatabase::class.java,
            "glucose_database"
        ).build()

        dao = db.glucoseDao()
    }

    /**
     * Fetch glucose data from xDrip and save to database
     * Returns true if new data was saved, false if data was already present (5-min optimization)
     */
    suspend fun fetchAndSave(phoneIp: String): Boolean {
        Log.d(TAG, "Fetching glucose data from $phoneIp:17580")
        
        try {
            // Build request
            val url = "http://$phoneIp:17580/sgv.json"
            val request = Request.Builder()
                .url(url)
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
            
            // Parse JSON response with timestamp checking
            val xDripReading = parseXDripResponseWithTimestamp(responseBody)
            
            // Only save if this is new data (5-minute optimization)
            if (!xDripReading.isNew) {
                Log.d(TAG, "Data already exists, skipping save (5-min optimization)")
                return false
            }
            
            // Create and save new reading
            val reading = GlucoseReading(
                timestamp = xDripReading.timestamp,
                glucoseValue = xDripReading.glucoseValue
            )
            
            withContext(Dispatchers.IO) {
                dao.insert(reading)
            }
            
            Log.d(TAG, "Successfully saved NEW glucose reading: ${xDripReading.glucoseValue} mg/dL at ${xDripReading.timestamp}")
            return true
            
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
        }
    }
    
    /**
     * Parse xDrip+ response with timestamp checking for duplicate detection
     */
    private suspend fun parseXDripResponseWithTimestamp(responseBody: String): XDripReading {
        val jsonObject = JSONObject(responseBody)
        
        // Extract glucose value
        val glucoseValue = when {
            jsonObject.has("sgv") -> jsonObject.getInt("sgv")
            jsonObject.has("glucose") -> jsonObject.getInt("glucose")
            jsonObject.has("bg") -> jsonObject.getInt("bg")
            else -> throw JSONException("No glucose value found in response")
        }
        
        // Extract timestamp (xDrip sends in milliseconds)
        val xDripTimestamp = when {
            jsonObject.has("date") -> jsonObject.getLong("date")
            jsonObject.has("timestamp") -> jsonObject.getLong("timestamp")
            jsonObject.has("time") -> jsonObject.getLong("time")
            else -> {
                Log.w(TAG, "No timestamp in xDrip response, using current time")
                System.currentTimeMillis()
            }
        }
        
        // Check if we already have this reading (within 30-second tolerance)
        val existingReading = withContext(Dispatchers.IO) {
            dao.getRecent().firstOrNull()
        }
        
        val isNew = existingReading == null || 
                   kotlin.math.abs(existingReading.timestamp - xDripTimestamp) > 30_000L
        
        Log.d(TAG, "xDrip reading: $glucoseValue mg/dL at $xDripTimestamp, isNew: $isNew")
        
        return XDripReading(
            glucoseValue = glucoseValue,
            timestamp = xDripTimestamp,
            isNew = isNew
        )
    }

    /**
     * Legacy method - parses glucose value only (kept for compatibility)
     */
    private fun parseXDripResponse(responseBody: String): Int {
        // First try parsing as JSON object (single reading)
        return try {
            val jsonObject = JSONObject(responseBody)
            when {
                jsonObject.has("sgv") -> jsonObject.getInt("sgv")
                jsonObject.has("glucose") -> jsonObject.getInt("glucose")
                jsonObject.has("bg") -> jsonObject.getInt("bg")
                else -> throw JSONException("No glucose value found in single object")
            }
        } catch (e: JSONException) {
            // If that fails, try parsing as JSON array (multiple readings)
            try {
                val jsonArray = JSONArray(responseBody)
                if (jsonArray.length() == 0) {
                    throw JSONException("Empty JSON array")
                }

                // Get the most recent reading (first element)
                val latestReading = jsonArray.getJSONObject(0)
                when {
                    latestReading.has("sgv") -> latestReading.getInt("sgv")
                    latestReading.has("glucose") -> latestReading.getInt("glucose")
                    latestReading.has("bg") -> latestReading.getInt("bg")
                    else -> throw JSONException("No glucose value found in array element")
                }
            } catch (arrayException: JSONException) {
                Log.e(TAG, "Failed to parse as both JSON object and array", arrayException)
                throw JSONException("Unable to parse glucose response: $responseBody")
            }
        }
    }
}
