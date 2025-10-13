/*
This file contains the class that acts to fetch the glucose data from the phone via HTTP
requests using BLE PAN.
 */

package com.example.karooglucometer.network

import android.content.Context
import androidx.room.Room
import com.example.karooglucometer.data.GlucoseDatabase
import com.example.karooglucometer.data.GlucoseReading
import kotlinx.coroutines.*
import okhttp3.*

class GlucoseFetcher(private val context: Context) {
    // OkHttpClient for network requests
    private val client = OkHttpClient()

    // Database setup
    private val db = Room.databaseBuilder(
        context,
        GlucoseDatabase::class.java,
        "glucose_db"
    ).build()

    // DAO for database operations
    private val dao = db.glucoseDao()

    // Fetch and save data from the given phone IP
    fun fetchAndSave(phoneIp: String) {
        // FYI: 17580 is the port xDrip broadcasts HTTP on.
        val url = "http://$phoneIp:17580/sgv.json"

        // Build the request
        val request = Request.Builder().url(url).build()

        // Queue the request and handle the response
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { e.printStackTrace() }
            override fun onResponse(call: Call, response: Response) {
                response.body.string().let { json ->
                    // Parse the glucose value from the JSON
                    val value = json.substringAfter("\"sgv\":")
                        .substringBefore(",").toIntOrNull() ?: return

                    // Create a new GlucoseReading and save it to the database
                    val reading = GlucoseReading(
                        timestamp = System.currentTimeMillis(),
                        glucoseValue = value,
                        trend = null
                    )

                    // Insert the reading into the database using thread safety
                    CoroutineScope(Dispatchers.IO).launch { dao.insert(reading) }
                }
            }
        })
    }
}
