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
 * Legacy Karoo Data Field Service for older Karoo firmware
 * Provides glucose data fields compatible with older Karoo software versions
 */
class KarooDataFieldService : Service() {
    private val TAG = "KarooDataFieldService"
    private val binder = LocalBinder()
    private var latestGlucoseReading: GlucoseReading? = null
    private var glucoseHistory: MutableList<GlucoseReading> = mutableListOf()
    private var lastBroadcastTime: Long = 0
    private var broadcastCount: Long = 0
    private var testModeEnabled = false
    
    inner class LocalBinder : Binder() {
        fun getService(): KarooDataFieldService = this@KarooDataFieldService
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Legacy Karoo Data Field Service bound")
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== LEGACY KAROO SERVICE STARTING ===")
        Log.d(TAG, "Service created at: ${System.currentTimeMillis()}")
        Log.d(TAG, "Package name: ${packageName}")
        Log.d(TAG, "Service class: ${this.javaClass.name}")
        
        // Test broadcast capability immediately
        testBroadcastCapability()
        
        Log.d(TAG, "=== LEGACY KAROO SERVICE READY ===")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Legacy Karoo Data Field Service started")
        
        // Handle legacy Karoo requests
        intent?.let { 
            try {
                handleLegacyKarooRequest(it) 
            } catch (e: Exception) {
                Log.e(TAG, "Error handling legacy Karoo request", e)
            }
        }
        
        return START_STICKY
    }
    
    /**
     * Handle legacy Karoo data field requests with comprehensive logging
     */
    private fun handleLegacyKarooRequest(intent: Intent) {
        val action = intent.action
        val extras = intent.extras
        
        Log.d(TAG, "=== LEGACY KAROO REQUEST ===")
        Log.d(TAG, "Action: $action")
        Log.d(TAG, "Extras count: ${extras?.size() ?: 0}")
        
        extras?.keySet()?.forEach { key ->
            Log.d(TAG, "Extra: $key = ${extras.get(key)}")
        }
        
        when (action) {
            "com.hammerhead.karoo.DATA_FIELD_REQUEST" -> {
                Log.d(TAG, "📡 Processing data field request")
                sendLegacyDataFieldResponse()
            }
            "com.hammerhead.karoo.GET_DATA_FIELDS" -> {
                Log.d(TAG, "📋 Processing get data fields request")
                sendAvailableDataFields()
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown legacy Karoo action: $action")
            }
        }
        
        Log.d(TAG, "=== REQUEST PROCESSED ===")
    }
    
    /**
     * Send available data fields to legacy Karoo with validation
     */
    private fun sendAvailableDataFields() {
        try {
            Log.d(TAG, "📋 Preparing data fields response...")
            
            val responseIntent = Intent("com.hammerhead.karoo.DATA_FIELDS_RESPONSE").apply {
                // Simple data fields compatible with legacy Karoo
                putExtra("field_count", 3)
                putExtra("field_1_id", "glucose")
                putExtra("field_1_name", "Glucose")
                putExtra("field_1_unit", "mg/dL")
                putExtra("field_1_type", "numeric")
                putExtra("field_2_id", "glucose_trend")
                putExtra("field_2_name", "Trend")
                putExtra("field_2_unit", "")
                putExtra("field_2_type", "text")
                putExtra("field_3_id", "glucose_age")
                putExtra("field_3_name", "Age")
                putExtra("field_3_unit", "min")
                putExtra("field_3_type", "numeric")
                putExtra("package_name", packageName)
                putExtra("service_version", "1.0")
                putExtra("response_timestamp", System.currentTimeMillis())
            }
            
            sendBroadcast(responseIntent)
            broadcastCount++
            lastBroadcastTime = System.currentTimeMillis()
            
            Log.d(TAG, "✅ Data fields response sent (broadcast #$broadcastCount)")
            Log.d(TAG, "Response action: com.hammerhead.karoo.DATA_FIELDS_RESPONSE")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send data fields response", e)
        }
    }
    
    /**
     * Send current glucose data to legacy Karoo with comprehensive validation
     */
    private fun sendLegacyDataFieldResponse() {
        try {
            Log.d(TAG, "📡 Preparing glucose data response...")
            
            val reading = if (testModeEnabled) {
                // Use test data if in test mode
                GlucoseReading(
                    timestamp = System.currentTimeMillis(),
                    glucoseValue = 120 + (broadcastCount % 20).toInt() // Varying test values
                )
            } else {
                latestGlucoseReading
            }
            
            val responseIntent = Intent("com.hammerhead.karoo.DATA_FIELD_VALUES").apply {
                if (reading != null) {
                    val ageMinutes = (System.currentTimeMillis() - reading.timestamp) / 60000
                    val trend = calculateSimpleTrend()
                    
                    Log.d(TAG, "Glucose data: ${reading.glucoseValue} mg/dL, Age: ${ageMinutes}m, Trend: $trend")
                    
                    if (ageMinutes < 30 || testModeEnabled) { // Fresh data or test mode
                        putExtra("glucose", reading.glucoseValue.toString())
                        putExtra("glucose_trend", trend)
                        putExtra("glucose_age", ageMinutes.toString())
                        
                        // Legacy alert levels
                        val alertLevel = when {
                            reading.glucoseValue < 70 -> "low"
                            reading.glucoseValue > 180 -> "high"
                            else -> "normal"
                        }
                        putExtra("alert_level", alertLevel)
                        
                        Log.d(TAG, "✅ Fresh glucose data prepared: ${reading.glucoseValue} mg/dL ($alertLevel)")
                        
                    } else {
                        // Stale data
                        putExtra("glucose", "---")
                        putExtra("glucose_trend", "")
                        putExtra("glucose_age", "old")
                        putExtra("alert_level", "stale")
                        
                        Log.w(TAG, "⚠️ Stale glucose data (age: ${ageMinutes}m)")
                    }
                } else {
                    // No data
                    putExtra("glucose", "---")
                    putExtra("glucose_trend", "")
                    putExtra("glucose_age", "none")
                    putExtra("alert_level", "no_data")
                    
                    Log.w(TAG, "⚠️ No glucose data available")
                }
                
                putExtra("timestamp", System.currentTimeMillis())
                putExtra("package_name", packageName)
                putExtra("broadcast_count", broadcastCount)
                putExtra("test_mode", testModeEnabled)
            }
            
            sendBroadcast(responseIntent)
            broadcastCount++
            lastBroadcastTime = System.currentTimeMillis()
            
            Log.d(TAG, "✅ Glucose data response sent (broadcast #$broadcastCount)")
            Log.d(TAG, "Response action: com.hammerhead.karoo.DATA_FIELD_VALUES")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send glucose data response", e)
        }
    }
    
    /**
     * Calculate simple trend for legacy Karoo compatibility
     */
    private fun calculateSimpleTrend(): String {
        if (glucoseHistory.size < 2) return "→"
        
        val recent = glucoseHistory.take(3).sortedByDescending { it.timestamp }
        if (recent.size < 2) return "→"
        
        val diff = recent[0].glucoseValue - recent[1].glucoseValue
        
        return when {
            diff > 10 -> "↑↑"
            diff > 5 -> "↑"
            diff < -10 -> "↓↓" 
            diff < -5 -> "↓"
            else -> "→"
        }
    }
    
    /**
     * Update glucose data with comprehensive validation and testing
     */
    fun updateGlucoseReading(reading: GlucoseReading) {
        try {
            Log.d(TAG, "=== GLUCOSE UPDATE ===")
            Log.d(TAG, "New reading: ${reading.glucoseValue} mg/dL at ${reading.timestamp}")
            Log.d(TAG, "Test mode: $testModeEnabled")
            
            // Validate glucose reading
            if (reading.glucoseValue < 0 || reading.glucoseValue > 999) {
                Log.w(TAG, "⚠️ Invalid glucose value: ${reading.glucoseValue}")
                return
            }
            
            val previousReading = latestGlucoseReading
            latestGlucoseReading = reading
            
            // Add to history (keep last 5 readings)
            glucoseHistory.add(0, reading)
            if (glucoseHistory.size > 5) {
                glucoseHistory = glucoseHistory.take(5).toMutableList()
            }
            
            // Log trend change
            if (previousReading != null) {
                val change = reading.glucoseValue - previousReading.glucoseValue
                Log.d(TAG, "Change: ${if (change >= 0) "+" else ""}$change mg/dL")
            }
            
            Log.d(TAG, "History size: ${glucoseHistory.size} readings")
            Log.d(TAG, "✅ Glucose reading updated successfully")
            
            // Notify legacy Karoo of update
            notifyLegacyKarooUpdate()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating glucose reading", e)
        }
    }
    
    /**
     * Notify legacy Karoo that data has been updated with validation
     */
    private fun notifyLegacyKarooUpdate() {
        try {
            Log.d(TAG, "📢 Notifying legacy Karoo of data update...")
            
            val updateIntent = Intent("com.hammerhead.karoo.DATA_UPDATE").apply {
                putExtra("package_name", packageName)
                putExtra("has_new_data", true)
                putExtra("timestamp", System.currentTimeMillis())
                putExtra("broadcast_count", broadcastCount)
                putExtra("data_fields_available", 3)
            }
            
            sendBroadcast(updateIntent)
            broadcastCount++
            
            Log.d(TAG, "✅ Legacy Karoo update notification sent (broadcast #$broadcastCount)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to notify legacy Karoo of update", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Legacy Karoo Data Field Service destroyed")
    }
    
    /**
     * Test broadcast capability and permissions
     */
    private fun testBroadcastCapability() {
        try {
            Log.d(TAG, "Testing broadcast capability...")
            
            val testIntent = Intent("com.hammerhead.karoo.TEST_BROADCAST").apply {
                putExtra("test_message", "Karoo service is working")
                putExtra("timestamp", System.currentTimeMillis())
                putExtra("package_name", packageName)
            }
            
            sendBroadcast(testIntent)
            Log.d(TAG, "✅ Test broadcast sent successfully")
            
            // Also test with sticky broadcast
            try {
                @Suppress("DEPRECATION")
                sendStickyBroadcast(testIntent)
                Log.d(TAG, "✅ Sticky broadcast capability confirmed")
            } catch (e: SecurityException) {
                Log.w(TAG, "⚠️ Sticky broadcast permission missing: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Broadcast test failed", e)
        }
    }
    
    /**
     * Enable test mode with mock data
     */
    fun enableTestMode() {
        testModeEnabled = true
        Log.d(TAG, "Test mode enabled - will use mock glucose data")
        
        // Create test glucose reading
        val testReading = GlucoseReading(
            timestamp = System.currentTimeMillis(),
            glucoseValue = 120 // Normal test value
        )
        
        updateGlucoseReading(testReading)
    }
    
    /**
     * Get comprehensive service status for debugging
     */
    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "service_active" to true,
            "latest_reading" to (latestGlucoseReading?.glucoseValue ?: "none"),
            "reading_timestamp" to (latestGlucoseReading?.timestamp ?: 0),
            "history_size" to glucoseHistory.size,
            "broadcast_count" to broadcastCount,
            "last_broadcast" to lastBroadcastTime,
            "test_mode" to testModeEnabled,
            "package_name" to packageName
        )
    }
    
    /**
     * Force a test broadcast for validation
     */
    fun triggerTestBroadcast() {
        Log.d(TAG, "🧪 Triggering test broadcast...")
        enableTestMode()
        sendLegacyDataFieldResponse()
        sendAvailableDataFields()
    }
}