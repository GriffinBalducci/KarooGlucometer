package com.example.karooglucometer.karoo

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.karooglucometer.data.GlucoseReading
import java.text.SimpleDateFormat
import java.util.*

/**
 * Karoo Data Field Service - Provides glucose data fields to Karoo ride computer
 * This service registers with Karoo and provides real-time glucose monitoring data fields
 */
class KarooDataFieldService : Service() {
    private val TAG = "KarooDataFieldService"
    private val binder = LocalBinder()
    private var latestGlucoseReading: GlucoseReading? = null
    private var glucoseHistory: MutableList<GlucoseReading> = mutableListOf()
    
    inner class LocalBinder : Binder() {
        fun getService(): KarooDataFieldService = this@KarooDataFieldService
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Karoo Data Field Service bound")
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Karoo Data Field Service created")
        registerDataFields()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Karoo Data Field Service started with intent: ${intent?.action}")
        
        // Handle Karoo data field requests with error handling
        intent?.let { 
            try {
                handleKarooRequest(it) 
            } catch (e: Exception) {
                Log.e(TAG, "Error handling Karoo request", e)
            }
        }
        
        return START_STICKY // Keep service running for Karoo integration
    }
    
    /**
     * Register available data fields with Karoo system
     */
    private fun registerDataFields() {
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount < maxRetries) {
            try {
                val registerIntent = Intent("io.hammerhead.karoo.REGISTER_DATA_FIELDS").apply {
                    putExtra("package_name", packageName)
                    putExtra("service_name", KarooDataFieldService::class.java.name)
                    
                    // Define available data fields (ArrayList for proper serialization)
                    val dataFields = arrayListOf(
                        hashMapOf(
                            "id" to "glucose_current",
                            "name" to "Blood Glucose",
                            "description" to "Current glucose reading",
                            "unit" to "mg/dL",
                            "format" to "decimal",
                            "category" to "health",
                            "precision" to "0"
                        ),
                        hashMapOf(
                            "id" to "glucose_trend", 
                            "name" to "Glucose Trend",
                            "description" to "Glucose trend direction",
                            "unit" to "",
                            "format" to "string",
                            "category" to "health",
                            "precision" to "0"
                        ),
                        hashMapOf(
                            "id" to "glucose_time",
                            "name" to "Glucose Age",
                            "description" to "Minutes since last reading",
                            "unit" to "min",
                            "format" to "decimal",
                            "category" to "health",
                            "precision" to "0"
                        )
                    )
                    
                    putExtra("data_fields", dataFields)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                
                sendBroadcast(registerIntent)
                Log.d(TAG, "Successfully registered data fields with Karoo (attempt ${retryCount + 1})")
                return // Success, exit retry loop
                
            } catch (e: Exception) {
                retryCount++
                Log.w(TAG, "Failed to register data fields (attempt $retryCount): ${e.message}")
                
                if (retryCount >= maxRetries) {
                    Log.e(TAG, "Failed to register data fields after $maxRetries attempts", e)
                } else {
                    // Wait before retry
                    try {
                        Thread.sleep(1000L * retryCount) // Exponential backoff
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
            }
        }
    }
    
    /**
     * Handle data field requests from Karoo
     */
    private fun handleKarooRequest(intent: Intent) {
        val action = intent.action
        val fieldId = intent.getStringExtra("field_id")
        
        Log.d(TAG, "Karoo request - Action: $action, Field ID: $fieldId")
        
        when (action) {
            "io.hammerhead.karoo.GET_DATA_FIELD_VALUE" -> {
                fieldId?.let { id ->
                    val value = getDataFieldValue(id)
                    sendDataFieldResponse(id, value)
                }
            }
            "io.hammerhead.karoo.DATA_FIELD_ACTIVATED" -> {
                Log.d(TAG, "Data field activated: $fieldId")
                // Start more frequent updates when field is active
            }
            "io.hammerhead.karoo.DATA_FIELD_DEACTIVATED" -> {
                Log.d(TAG, "Data field deactivated: $fieldId")
                // Reduce update frequency when field is not displayed
            }
        }
    }
    
    /**
     * Get current value for a specific data field
     */
    private fun getDataFieldValue(fieldId: String): String {
        val reading = latestGlucoseReading
        
        return when (fieldId) {
            "glucose_current" -> {
                if (reading != null) {
                    val age = (System.currentTimeMillis() - reading.timestamp) / 60000
                    if (age < 20) { // Extended freshness for cycling
                        reading.glucoseValue.toString()
                    } else {
                        "0" // Return 0 for stale data (Karoo expects numeric)
                    }
                } else {
                    "0" // No data - return 0
                }
            }
            
            "glucose_trend" -> {
                calculateTrendIndicator()
            }
            
            "glucose_time" -> {
                if (reading != null) {
                    val age = (System.currentTimeMillis() - reading.timestamp) / 60000
                    age.toString() // Return age in minutes
                } else {
                    "999" // Large number indicates no data
                }
            }
            
            else -> "0"
        }
    }
    
    /**
     * Calculate trend indicator based on recent glucose readings
     * Returns simple text indicators that work well on Karoo displays
     */
    private fun calculateTrendIndicator(): String {
        if (glucoseHistory.size < 2) return "STABLE"
        
        val recent = glucoseHistory.take(5).sortedByDescending { it.timestamp }
        if (recent.size < 2) return "STABLE"
        
        // Calculate average trend over multiple points for stability
        val trends = mutableListOf<Int>()
        for (i in 0 until (recent.size - 1).coerceAtMost(3)) {
            trends.add(recent[i].glucoseValue - recent[i + 1].glucoseValue)
        }
        
        val avgTrend = trends.average()
        
        return when {
            avgTrend > 8 -> "RISING FAST"  
            avgTrend > 3 -> "RISING"       
            avgTrend < -8 -> "FALLING FAST" 
            avgTrend < -3 -> "FALLING"     
            else -> "STABLE"               
        }
    }
    
    /**
     * Send data field value response back to Karoo
     */
    private fun sendDataFieldResponse(fieldId: String, value: String) {
        try {
            val responseIntent = Intent("io.hammerhead.karoo.DATA_FIELD_RESPONSE").apply {
                putExtra("field_id", fieldId)
                putExtra("field_value", value)
                putExtra("timestamp", System.currentTimeMillis())
                
                // Add metadata for enhanced display
                latestGlucoseReading?.let { reading ->
                    val age = (System.currentTimeMillis() - reading.timestamp) / 60000
                    putExtra("data_age_minutes", age)
                    
                    // Alert levels for color coding
                    when {
                        reading.glucoseValue < 70 -> putExtra("alert_level", "critical")
                        reading.glucoseValue > 180 -> putExtra("alert_level", "warning")
                        else -> putExtra("alert_level", "normal")
                    }
                }
            }
            
            sendBroadcast(responseIntent)
            Log.d(TAG, "Sent data field response: $fieldId = $value")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data field response", e)
        }
    }
    
    /**
     * Update glucose data (called from main app)
     */
    fun updateGlucoseReading(reading: GlucoseReading) {
        try {
            // Validate glucose reading
            if (reading.glucoseValue < 0 || reading.glucoseValue > 999) {
                Log.w(TAG, "Invalid glucose value: ${reading.glucoseValue}")
                return
            }
            
            latestGlucoseReading = reading
            
            // Add to history (keep last 10 readings for trend calculation)
            glucoseHistory.add(0, reading)
            if (glucoseHistory.size > 10) {
                glucoseHistory = glucoseHistory.take(10).toMutableList()
            }
            
            Log.d(TAG, "Updated glucose reading: ${reading.glucoseValue} mg/dL at ${reading.timestamp}")
            
            // Notify Karoo of data update
            notifyDataUpdate()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating glucose reading", e)
        }
    }
    
    /**
     * Notify Karoo that data has been updated
     */
    private fun notifyDataUpdate() {
        try {
            val updateIntent = Intent("io.hammerhead.karoo.DATA_FIELD_UPDATE").apply {
                putExtra("package_name", packageName)
                putExtra("timestamp", System.currentTimeMillis())
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            
            sendBroadcast(updateIntent)
            Log.v(TAG, "Notified Karoo of data update") // Verbose logging to reduce spam
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify Karoo of data update", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Karoo Data Field Service destroyed")
    }
}