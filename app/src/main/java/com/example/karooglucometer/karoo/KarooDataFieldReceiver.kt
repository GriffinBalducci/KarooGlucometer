package com.example.karooglucometer.karoo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver for legacy Karoo data field discovery
 * Handles discovery requests from older Karoo firmware
 */
class KarooDataFieldReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "KarooDataFieldReceiver"
        private var receiveCount = 0
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        receiveCount++
        
        Log.d(TAG, "=== LEGACY KAROO BROADCAST RECEIVED (#$receiveCount) ===")
        Log.d(TAG, "Action: ${intent.action}")
        Log.d(TAG, "Timestamp: ${System.currentTimeMillis()}")
        Log.d(TAG, "Package: ${intent.`package` ?: "null"}")
        Log.d(TAG, "Categories: ${intent.categories?.joinToString(", ") ?: "none"}")
        Log.d(TAG, "Scheme: ${intent.scheme ?: "none"}")
        Log.d(TAG, "Data: ${intent.data ?: "none"}")
        
        intent.extras?.let { extras ->
            Log.d(TAG, "Extras count: ${extras.size()}")
            extras.keySet().forEach { key ->
                Log.d(TAG, "  $key = ${extras.get(key)}")
            }
        } ?: Log.d(TAG, "No extras")
        
        when (intent.action) {
            "com.hammerhead.karoo.DISCOVER_DATA_PROVIDERS" -> {
                Log.d(TAG, "📡 Processing discovery request...")
                handleDiscoveryRequest(context)
            }
            "com.hammerhead.karoo.REQUEST_DATA_FIELDS" -> {
                Log.d(TAG, "📋 Processing data field request...")
                handleDataFieldRequest(context)
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown broadcast action: ${intent.action}")
                // Still try to respond with discovery information
                Log.d(TAG, "📡 Sending fallback discovery response...")
                handleDiscoveryRequest(context)
            }
        }
        
        Log.d(TAG, "=== BROADCAST PROCESSED ===")
    }
    
    /**
     * Handle legacy Karoo discovery request with comprehensive validation
     */
    private fun handleDiscoveryRequest(context: Context) {
        try {
            Log.d(TAG, "🔍 Preparing discovery response...")
            
            val responseIntent = Intent("com.hammerhead.karoo.DATA_PROVIDER_DISCOVERED").apply {
                putExtra("package_name", context.packageName)
                putExtra("service_name", "com.example.karooglucometer.karoo.KarooDataFieldService")
                putExtra("provider_name", "Glucose Monitor")
                putExtra("provider_description", "Real-time blood glucose from xDrip+")
                putExtra("data_field_count", 3)
                putExtra("supports_legacy_api", true)
                putExtra("api_version", "legacy_1.0")
                putExtra("response_timestamp", System.currentTimeMillis())
                putExtra("discovery_response_id", receiveCount)
            }
            
            context.sendBroadcast(responseIntent)
            Log.d(TAG, "✅ Discovery response sent")
            Log.d(TAG, "Response action: com.hammerhead.karoo.DATA_PROVIDER_DISCOVERED")
            Log.d(TAG, "Package: ${context.packageName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send discovery response", e)
        }
    }
    
    /**
     * Handle legacy data field request with comprehensive validation
     */
    private fun handleDataFieldRequest(context: Context) {
        try {
            Log.d(TAG, "📋 Preparing data fields definition response...")
            
            val responseIntent = Intent("com.hammerhead.karoo.DATA_FIELDS_AVAILABLE").apply {
                putExtra("package_name", context.packageName)
                putExtra("response_timestamp", System.currentTimeMillis())
                putExtra("request_response_id", receiveCount)
                
                // Data field definitions for legacy Karoo
                putExtra("field_count", 3)
                
                // Glucose value field
                putExtra("field_1_id", "glucose")
                putExtra("field_1_name", "Glucose")
                putExtra("field_1_description", "Current blood glucose reading")
                putExtra("field_1_unit", "mg/dL")
                putExtra("field_1_type", "numeric")
                putExtra("field_1_format", "%.0f")
                putExtra("field_1_precision", 0)
                
                // Trend field
                putExtra("field_2_id", "glucose_trend")
                putExtra("field_2_name", "Trend")
                putExtra("field_2_description", "Glucose trend direction")
                putExtra("field_2_unit", "")
                putExtra("field_2_type", "text")
                putExtra("field_2_format", "%s")
                
                // Age field
                putExtra("field_3_id", "glucose_age")
                putExtra("field_3_name", "Age")
                putExtra("field_3_description", "Minutes since last reading")
                putExtra("field_3_unit", "min")
                putExtra("field_3_type", "numeric")
                putExtra("field_3_format", "%.0f")
                putExtra("field_3_precision", 0)
                
                // Additional metadata
                putExtra("provider_name", "Glucose Monitor")
                putExtra("data_source", "xDrip+")
            }
            
            context.sendBroadcast(responseIntent)
            Log.d(TAG, "✅ Data fields definition response sent")
            Log.d(TAG, "Response action: com.hammerhead.karoo.DATA_FIELDS_AVAILABLE")
            Log.d(TAG, "Fields defined: glucose, glucose_trend, glucose_age")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send data fields response", e)
        }
    }
}