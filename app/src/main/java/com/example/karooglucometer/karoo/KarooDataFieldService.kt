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
        Log.d(TAG, "Karoo Data Field Service started")
        
        // Handle Karoo data field requests
        intent?.let { handleKarooRequest(it) }
        
        return START_STICKY // Keep service running
    }
    
    /**
     * Register available data fields with Karoo system
     */
    private fun registerDataFields() {
        try {
            val registerIntent = Intent("io.hammerhead.karoo.REGISTER_DATA_FIELDS").apply {
                putExtra("package_name", packageName)
                putExtra("service_name", KarooDataFieldService::class.java.name)
                
                // Define available data fields
                val dataFields = arrayOf(
                    mapOf(
                        "id" to "glucose_current",
                        "name" to "Glucose",
                        "description" to "Current blood glucose level",
                        "unit" to "mg/dL",
                        "format" to "number",
                        "category" to "health"
                    ),
                    mapOf(
                        "id" to "glucose_trend", 
                        "name" to "Glucose Trend",
                        "description" to "Blood glucose trend direction",
                        "unit" to "arrow",
                        "format" to "symbol",
                        "category" to "health"
                    ),
                    mapOf(
                        "id" to "glucose_time",
                        "name" to "Glucose Time",
                        "description" to "Time of last glucose reading",
                        "unit" to "time",
                        "format" to "time",
                        "category" to "health"
                    )
                )
                
                putExtra("data_fields", dataFields)
            }
            
            sendBroadcast(registerIntent)
            Log.d(TAG, "Registered data fields with Karoo")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register data fields with Karoo", e)
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
                    if (age < 15) { // Data is fresh (less than 15 minutes old)
                        reading.glucoseValue.toString()
                    } else {
                        "OLD" // Indicate stale data
                    }
                } else {
                    "---" // No data available
                }
            }
            
            "glucose_trend" -> {
                calculateTrendArrow()
            }
            
            "glucose_time" -> {
                if (reading != null) {
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    formatter.format(Date(reading.timestamp))
                } else {
                    "--:--"
                }
            }
            
            else -> "N/A"
        }
    }
    
    /**
     * Calculate trend arrow based on recent glucose readings
     */
    private fun calculateTrendArrow(): String {
        if (glucoseHistory.size < 2) return "→"
        
        val recent = glucoseHistory.take(3) // Last 3 readings
        if (recent.size < 2) return "→"
        
        val latest = recent[0].glucoseValue
        val previous = recent[1].glucoseValue
        val trend = latest - previous
        
        return when {
            trend > 5 -> "↗↗"  // Rapidly rising
            trend > 2 -> "↗"   // Rising
            trend < -5 -> "↘↘" // Rapidly falling  
            trend < -2 -> "↘"  // Falling
            else -> "→"        // Stable
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
        latestGlucoseReading = reading
        
        // Add to history (keep last 10 readings for trend calculation)
        glucoseHistory.add(0, reading)
        if (glucoseHistory.size > 10) {
            glucoseHistory = glucoseHistory.take(10).toMutableList()
        }
        
        Log.d(TAG, "Updated glucose reading: ${reading.glucoseValue} mg/dL")
        
        // Notify Karoo of data update
        notifyDataUpdate()
    }
    
    /**
     * Notify Karoo that data has been updated
     */
    private fun notifyDataUpdate() {
        try {
            val updateIntent = Intent("io.hammerhead.karoo.DATA_FIELD_UPDATE").apply {
                putExtra("package_name", packageName)
                putExtra("timestamp", System.currentTimeMillis())
            }
            
            sendBroadcast(updateIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify Karoo of data update", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Karoo Data Field Service destroyed")
    }
}