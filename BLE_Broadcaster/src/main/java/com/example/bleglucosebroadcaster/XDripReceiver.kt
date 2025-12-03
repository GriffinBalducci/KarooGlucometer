package com.example.bleglucosebroadcaster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class XDripReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "XDripReceiver"
        private var listener: GlucoseUpdateListener? = null

        fun setListener(listener: GlucoseUpdateListener?) {
            this.listener = listener
        }
    }

    interface GlucoseUpdateListener {
        fun onGlucoseUpdate(glucose: Int, timestamp: Long, delta: String?)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        Log.d(TAG, "Received broadcast: ${intent.action}")

        try {
            // Log all extras for debugging
            intent.extras?.let { bundle ->
                for (key in bundle.keySet()) {
                    Log.d(TAG, "Extra: $key = ${bundle.get(key)}")
                }
            }

            // Try different field names that xDrip+ might use
            val glucose = when {
                intent.hasExtra("glucoseValue") -> intent.getDoubleExtra("glucoseValue", -1.0)
                intent.hasExtra("glucose") -> intent.getDoubleExtra("glucose", -1.0)
                intent.hasExtra("bgEstimate") -> intent.getDoubleExtra("bgEstimate", -1.0)
                intent.hasExtra("com.eveningoutpost.dexdrip.Extras.BgEstimate") ->
                    intent.getDoubleExtra("com.eveningoutpost.dexdrip.Extras.BgEstimate", -1.0)
                else -> -1.0
            }

            val timestamp = when {
                intent.hasExtra("timestamp") -> intent.getLongExtra("timestamp", System.currentTimeMillis())
                intent.hasExtra("com.eveningoutpost.dexdrip.Extras.Time") ->
                    intent.getLongExtra("com.eveningoutpost.dexdrip.Extras.Time", System.currentTimeMillis())
                else -> System.currentTimeMillis()
            }

            val delta = intent.getStringExtra("delta")
                ?: intent.getStringExtra("com.eveningoutpost.dexdrip.Extras.DeltaName")

            if (glucose > 0) {
                Log.d(TAG, "Received xDrip glucose: $glucose mg/dL, delta: $delta, timestamp: $timestamp")
                listener?.onGlucoseUpdate(glucose.toInt(), timestamp, delta)
            } else {
                Log.w(TAG, "Invalid glucose value: $glucose")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing xDrip broadcast", e)
        }
    }
}