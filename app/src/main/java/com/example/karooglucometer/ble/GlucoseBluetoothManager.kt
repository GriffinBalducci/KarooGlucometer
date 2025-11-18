package com.example.karooglucometer.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * BLE GATT glucose monitor scanner and connection manager
 */
class GlucoseBluetoothManager(private val context: Context) {
    companion object {
        private const val TAG = "GlucoseBluetoothManager"
        private const val SCAN_PERIOD: Long = 10000 // 10 seconds
        
        // Glucose Service UUID (standard Bluetooth SIG)
        private val GLUCOSE_SERVICE_UUID = UUID.fromString("00001808-0000-1000-8000-00805F9B34FB")
        private val GLUCOSE_MEASUREMENT_UUID = UUID.fromString("00002A18-0000-1000-8000-00805F9B34FB")
        private val GLUCOSE_CONTEXT_UUID = UUID.fromString("00002A34-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State flows for reactive UI
    private val _connectionState = MutableStateFlow(BluetoothGattState.DISCONNECTED)
    val connectionState: StateFlow<BluetoothGattState> = _connectionState.asStateFlow()

    private val _glucoseReadings = MutableStateFlow<List<GlucoseReading>>(emptyList())
    val glucoseReadings: StateFlow<List<GlucoseReading>> = _glucoseReadings.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    enum class BluetoothGattState {
        DISCONNECTED, CONNECTING, CONNECTED, DISCOVERING_SERVICES
    }

    data class GlucoseReading(
        val value: Double,
        val timestamp: Long,
        val unit: String = "mg/dL",
        val trend: String = "Unknown",
        val source: String = "BLE"
    )

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Missing Bluetooth permissions")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth is not enabled")
            return
        }

        if (_isScanning.value) {
            Log.d(TAG, "Already scanning")
            return
        }

        Log.d(TAG, "Starting BLE scan for glucose monitors")
        _isScanning.value = true

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(GLUCOSE_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { handleScanResult(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

        bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)

        // Stop scan after period
        handler.postDelayed({
            stopScan()
        }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        
        Log.d(TAG, "Stopping BLE scan")
        scanCallback?.let { bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val deviceName = device.name ?: "Unknown"
        
        Log.d(TAG, "Found glucose monitor: $deviceName (${device.address}) RSSI: ${result.rssi}")
        
        // Auto-connect to first glucose monitor found
        if (_connectionState.value == BluetoothGattState.DISCONNECTED) {
            connectToDevice(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to glucose monitor: ${device.name} (${device.address})")
        _connectionState.value = BluetoothGattState.CONNECTING
        
        stopScan() // Stop scanning when connecting

        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to glucose monitor")
                        _connectionState.value = BluetoothGattState.CONNECTED
                        // Discover services
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from glucose monitor")
                        _connectionState.value = BluetoothGattState.DISCONNECTED
                        cleanup()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered, setting up glucose notifications")
                    _connectionState.value = BluetoothGattState.DISCOVERING_SERVICES
                    setupGlucoseNotifications(gatt)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                characteristic?.let { handleGlucoseData(it) }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun setupGlucoseNotifications(gatt: BluetoothGatt?) {
        val glucoseService = gatt?.getService(GLUCOSE_SERVICE_UUID)
        val glucoseCharacteristic = glucoseService?.getCharacteristic(GLUCOSE_MEASUREMENT_UUID)

        glucoseCharacteristic?.let { characteristic ->
            gatt.setCharacteristicNotification(characteristic, true)
            
            // Enable notifications
            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            
            Log.d(TAG, "Glucose notifications enabled")
        }
    }

    private fun handleGlucoseData(characteristic: BluetoothGattCharacteristic) {
        val data = characteristic.value
        if (data != null && data.size >= 6) {
            // Parse glucose measurement according to Bluetooth spec
            val flags = data[0].toInt() and 0xFF
            var offset = 1
            
            // Sequence number (2 bytes)
            val sequenceNumber = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
            offset += 2
            
            // Glucose concentration (2 bytes, SFLOAT format)
            val glucoseValue = parseSFloat(data, offset)
            offset += 2
            
            val reading = GlucoseReading(
                value = glucoseValue,
                timestamp = System.currentTimeMillis(),
                unit = if ((flags and 0x04) != 0) "mmol/L" else "mg/dL",
                trend = "Stable", // Could be parsed from context if available
                source = "BLE"
            )
            
            Log.d(TAG, "Received glucose reading: ${reading.value} ${reading.unit}")
            
            // Update readings
            val currentReadings = _glucoseReadings.value.toMutableList()
            currentReadings.add(reading)
            _glucoseReadings.value = currentReadings.takeLast(20) // Keep last 20 readings
        }
    }

    private fun parseSFloat(data: ByteArray, offset: Int): Double {
        if (offset + 1 >= data.size) return 0.0
        
        val value = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
        
        val mantissa = value and 0x0FFF
        val exponent = (value shr 12) and 0x0F
        
        return mantissa * Math.pow(10.0, exponent.toDouble())
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        cleanup()
    }

    private fun cleanup() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        stopScan()
    }

    fun destroy() {
        disconnect()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}