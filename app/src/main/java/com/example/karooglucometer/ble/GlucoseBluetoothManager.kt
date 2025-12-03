package com.example.karooglucometer.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class GlucoseBluetoothManager(private val context: Context) {
    companion object {
        private const val TAG = "GlucoseBluetoothManager"
        private const val SCAN_PERIOD: Long = 10_000L

        // MUST MATCH PHONE SIDE EXACTLY
        private val CUSTOM_UUID: UUID =
            UUID.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState =
        MutableStateFlow(BluetoothGattState.DISCONNECTED)
    val connectionState: StateFlow<BluetoothGattState> =
        _connectionState.asStateFlow()

    private val _glucoseReadings =
        MutableStateFlow<List<GlucoseReading>>(emptyList())
    val glucoseReadings: StateFlow<List<GlucoseReading>> =
        _glucoseReadings.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    enum class BluetoothGattState {
        DISCONNECTED, SCANNING
    }

    data class GlucoseReading(
        val value: Double,
        val timestamp: Long,
        val unit: String = "mg/dL",
        val trend: String = "Unknown",
        val source: String = "BLE"
    )

    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) {
            Log.w(TAG, "Missing Bluetooth permissions")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not enabled")
            return
        }

        if (_isScanning.value) {
            Log.d(TAG, "Already scanning")
            return
        }

        Log.d(TAG, "Starting BLE scan (NO FILTER, debug mode)")
        _isScanning.value = true
        _connectionState.value = BluetoothGattState.SCANNING

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleAdvertisement(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleAdvertisement(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: $errorCode")
                _isScanning.value = false
                _connectionState.value = BluetoothGattState.DISCONNECTED
            }
        }

        // 🔴 NO FILTER: scan everything for now
        scanner?.startScan(null, settings, scanCallback)

        handler.postDelayed({ stopScan() }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return

        Log.d(TAG, "Stopping BLE scan")
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
        _isScanning.value = false
        _connectionState.value = BluetoothGattState.DISCONNECTED
    }

    private fun handleAdvertisement(result: ScanResult) {
        val record = result.scanRecord ?: return

        val addr = result.device.address
        val name = record.deviceName ?: result.device.name ?: "null"
        Log.d(TAG, "ADV from $addr name=$name rssi=${result.rssi}")

        val serviceDataMap = record.serviceData
        if (serviceDataMap != null && serviceDataMap.isNotEmpty()) {
            for ((uuid, data) in serviceDataMap) {
                val first = if (data.isNotEmpty()) (data[0].toInt() and 0xFF) else -1
                Log.d(TAG, "ServiceData UUID=$uuid len=${data.size} firstByte=$first")
            }
        }

        // Now try our custom UUID specifically
        val ourData = record.getServiceData(ParcelUuid(CUSTOM_UUID)) ?: return
        if (ourData.isEmpty()) return

        val glucoseByte = ourData[0].toInt() and 0xFF
        val glucoseValue = glucoseByte.toDouble()

        Log.d(TAG, "✅ Received glucose from adv: $glucoseValue mg/dL")

        val reading = GlucoseReading(
            value = glucoseValue,
            timestamp = System.currentTimeMillis(),
            unit = "mg/dL",
            trend = "Stable",
            source = "BLE"
        )

        val current = _glucoseReadings.value.toMutableList()
        current.add(reading)
        _glucoseReadings.value = current.takeLast(20)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
    }

    fun destroy() {
        stopScan()
        handler.removeCallbacksAndMessages(null)
    }

    private fun hasPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
