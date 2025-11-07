package com.example.karooglucometer.karoo

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.karooglucometer.data.GlucoseReading
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service to publish glucose data to Karoo ride metrics
 * Uses broadcast intents to communicate with Karoo system
 */
class KarooMetricsService(private val context: Context) {
    private val TAG = "KarooMetricsService"
    
    companion object {
        // Karoo metric broadcast constants
        const val KAROO_METRIC_ACTION = "io.hammerhead.karoo.METRIC_UPDATE"
        const val KAROO_METRIC_GLUCOSE = "glucose"
        const val KAROO_PERMISSION = "io.hammerhead.karoo.permission.METRIC_ACCESS"
    }
    
    /**
     * Publish latest glucose reading to Karoo ride metrics
     */
    fun publishGlucoseMetric(reading: GlucoseReading) {
        try {
            val intent = Intent(KAROO_METRIC_ACTION).apply {
                // Glucose value as primary metric
                putExtra("metric_id", KAROO_METRIC_GLUCOSE)
                putExtra("metric_value", reading.glucoseValue)
                putExtra("metric_unit", "mg/dL")
                putExtra("metric_type", "gauge") // Real-time value
                
                // Additional metadata
                putExtra("metric_display_name", "Blood Glucose")
                putExtra("metric_timestamp", reading.timestamp)
                putExtra("metric_source", "xDrip+")
                
                // Data freshness indicator
                val minutesAgo = (System.currentTimeMillis() - reading.timestamp) / 60000
                putExtra("metric_age_minutes", minutesAgo)
                
                // Alert levels for Karoo display
                when {
                    reading.glucoseValue < 70 -> {
                        putExtra("metric_alert_level", "critical")
                        putExtra("metric_alert_message", "LOW GLUCOSE")
                    }
                    reading.glucoseValue > 180 -> {
                        putExtra("metric_alert_level", "warning") 
                        putExtra("metric_alert_message", "HIGH GLUCOSE")
                    }
                    else -> {
                        putExtra("metric_alert_level", "normal")
                    }
                }
                
                // Formatted display value
                putExtra("metric_display_value", "${reading.glucoseValue} mg/dL")
            }
            
            // Send broadcast to Karoo system
            context.sendBroadcast(intent, KAROO_PERMISSION)
            
            Log.d(TAG, "Published glucose metric: ${reading.glucoseValue} mg/dL to Karoo")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish glucose metric to Karoo", e)
        }
    }
    
    /**
     * Publish glucose trend data for advanced Karoo displays
     */
    fun publishGlucoseTrend(readings: List<GlucoseReading>) {
        if (readings.size < 2) return
        
        try {
            val latest = readings.first()
            val previous = readings[1]
            val trend = latest.glucoseValue - previous.glucoseValue
            
            val trendIntent = Intent(KAROO_METRIC_ACTION).apply {
                putExtra("metric_id", "glucose_trend")
                putExtra("metric_value", trend)
                putExtra("metric_unit", "mg/dL/reading")
                putExtra("metric_type", "delta")
                putExtra("metric_display_name", "Glucose Trend")
                
                // Trend direction indicator
                val trendDirection = when {
                    trend > 5 -> "rapidly_rising"
                    trend > 2 -> "rising"
                    trend < -5 -> "rapidly_falling"
                    trend < -2 -> "falling"
                    else -> "stable"
                }
                putExtra("metric_trend_direction", trendDirection)
                
                // Arrow symbol for display
                val trendSymbol = when {
                    trend > 5 -> "↗↗"
                    trend > 2 -> "↗"
                    trend < -5 -> "↘↘" 
                    trend < -2 -> "↘"
                    else -> "→"
                }
                putExtra("metric_display_value", "$trendSymbol ${if (trend > 0) "+" else ""}$trend")
            }
            
            context.sendBroadcast(trendIntent, KAROO_PERMISSION)
            
            Log.d(TAG, "Published glucose trend: $trend mg/dL to Karoo")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish glucose trend to Karoo", e)
        }
    }
    
    /**
     * Register glucose data source with Karoo
     */
    fun registerGlucoseDataSource() {
        try {
            val registerIntent = Intent("io.hammerhead.karoo.REGISTER_METRIC_SOURCE").apply {
                putExtra("source_id", "xdrip_glucose")
                putExtra("source_name", "xDrip+ Glucose Monitor")
                putExtra("source_description", "Real-time blood glucose monitoring")
                putExtra("source_version", "1.0")
                
                // Available metrics
                putExtra("metrics", arrayOf(
                    mapOf(
                        "id" to KAROO_METRIC_GLUCOSE,
                        "name" to "Blood Glucose",
                        "unit" to "mg/dL",
                        "type" to "gauge",
                        "critical_low" to 70,
                        "warning_low" to 80,
                        "warning_high" to 180,
                        "critical_high" to 250
                    ),
                    mapOf(
                        "id" to "glucose_trend",
                        "name" to "Glucose Trend", 
                        "unit" to "mg/dL/reading",
                        "type" to "delta"
                    )
                ))
            }
            
            context.sendBroadcast(registerIntent, KAROO_PERMISSION)
            Log.d(TAG, "Registered glucose data source with Karoo")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register glucose data source with Karoo", e)
        }
    }
    
    /**
     * Get formatted timestamp for Karoo display
     */
    private fun getFormattedTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}