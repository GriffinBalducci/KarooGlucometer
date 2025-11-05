package com.example.karooglucometer.extensions

import com.example.karooglucometer.data.GlucoseReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper extensions for GlucoseReading to make UI code cleaner
 */

fun GlucoseReading.getFormattedTime(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun GlucoseReading.getFormattedDateTime(): String {
    val formatter = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun GlucoseReading.isStale(): Boolean {
    val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000L)
    return timestamp < tenMinutesAgo
}

fun GlucoseReading.getDisplayValue(): String = glucoseValue.toString()

fun GlucoseReading.getMinutesAgo(): Long {
    return (System.currentTimeMillis() - timestamp) / (60 * 1000L)
}

fun List<GlucoseReading>.getTrend(): String {
    if (size < 2) return "—"
    
    val latest = first().glucoseValue
    val previous = get(1).glucoseValue
    val diff = latest - previous
    
    return when {
        diff > 5 -> "↗"
        diff < -5 -> "↘" 
        else -> "→"
    }
}