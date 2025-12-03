package com.example.karooglucometer.karoo

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.karooglucometer.data.GlucoseReading

class KarooDataFieldService : Service() {
    companion object {
        private const val TAG = "KarooDataFieldService"
    }

    private val binder = LocalBinder()
    private var testMode = false
    private var lastGlucoseReading: GlucoseReading? = null

    inner class LocalBinder : Binder() {
        fun getService(): KarooDataFieldService = this@KarooDataFieldService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Karoo Data Field Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Karoo Data Field Service destroyed")
    }

    fun updateGlucoseReading(reading: GlucoseReading) {
        lastGlucoseReading = reading
        Log.d(TAG, "Updated glucose reading: ${reading.glucoseValue} mg/dL")
        
        // Send broadcast to Karoo if needed
        val intent = Intent("com.hammerhead.karoo.DATA_FIELD_UPDATE")
        intent.putExtra("glucose_value", reading.glucoseValue)
        intent.putExtra("timestamp", reading.timestamp)
        sendBroadcast(intent)
    }

    fun enableTestMode() {
        testMode = true
        Log.d(TAG, "Test mode enabled")
    }

    fun triggerTestBroadcast() {
        Log.d(TAG, "Triggering test broadcast")
        val testReading = GlucoseReading(
            timestamp = System.currentTimeMillis(),
            glucoseValue = 120
        )
        updateGlucoseReading(testReading)
    }

    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "testMode" to testMode,
            "lastReading" to (lastGlucoseReading?.glucoseValue ?: "None"),
            "lastUpdate" to (lastGlucoseReading?.timestamp ?: 0L)
        )
    }
}