package com.example.bleglucosebroadcaster

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.random.Random

@SuppressLint("MissingPermission")
class GlucoseGattService : Service() {
    companion object {
        private const val TAG = "GlucoseGattService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "glucose_broadcaster_channel"
        
        // Standard Bluetooth SIG UUIDs for Glucose Service
        val GLUCOSE_SERVICE_UUID: UUID = UUID.fromString("00001808-0000-1000-8000-00805F9B34FB")
        val GLUCOSE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A18-0000-1000-8000-00805F9B34FB")
        val GLUCOSE_CONTEXT_UUID: UUID = UUID.fromString("00002A34-0000-1000-8000-00805F9B34FB")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        // Custom service for easier testing (fallback)
        val CUSTOM_GLUCOSE_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        val CUSTOM_GLUCOSE_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    }
    
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertisingCallback: AdvertisingCallback? = null
    
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private var currentGlucoseValue = 120 // mg/dL
    private var sequenceNumber = 0
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    inner class LocalBinder : Binder() {
        fun getService(): GlucoseGattService = this@GlucoseGattService
    }
    
    private val binder = LocalBinder()
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        
        createNotificationChannel()
        setupGattServer()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when {
                it.hasExtra("glucose_value") -> {
                    currentGlucoseValue = it.getIntExtra("glucose_value", 120)
                    startForegroundService()
                    startAdvertising()
                }
                it.hasExtra("update_glucose") -> {
                    currentGlucoseValue = it.getIntExtra("update_glucose", 120)
                    broadcastGlucoseReading()
                }
            }
        }
        
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Glucose Broadcaster",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Broadcasting glucose data via BLE"
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glucose Broadcaster")
            .setContentText("Broadcasting ${currentGlucoseValue} mg/dL via BLE")
            .setSmallIcon(android.R.drawable.ic_bluetooth)
            .setOngoing(true)
            .build()
    }
    
    private fun setupGattServer() {
        val gattServerCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                device?.let {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connectedDevices.add(it)
                            Log.d(TAG, "Device connected: ${it.address}")
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectedDevices.remove(it)
                            Log.d(TAG, "Device disconnected: ${it.address}")
                        }
                    }
                }
            }
            
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                when (characteristic?.uuid) {
                    GLUCOSE_MEASUREMENT_UUID -> {
                        val glucoseData = buildGlucoseNotification()
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, glucoseData
                        )
                    }
                    CUSTOM_GLUCOSE_CHAR_UUID -> {
                        val simpleData = ByteBuffer.allocate(4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(currentGlucoseValue)
                            .array()
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, simpleData
                        )
                    }
                }
            }
            
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                descriptor: BluetoothGattDescriptor?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (descriptor?.uuid == CLIENT_CHARACTERISTIC_CONFIG) {
                    if (responseNeeded) {
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null
                        )
                    }
                    
                    // Start notifications if enabled
                    value?.let { data ->
                        if (data.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                            Log.d(TAG, "Notifications enabled for device: ${device?.address}")
                        }
                    }
                }
            }
        }
        
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        
        // Add glucose service
        val glucoseService = createGlucoseService()
        bluetoothGattServer?.addService(glucoseService)
        
        // Add custom service for easier testing
        val customService = createCustomGlucoseService()
        bluetoothGattServer?.addService(customService)
    }
    
    private fun createGlucoseService(): BluetoothGattService {
        val service = BluetoothGattService(
            GLUCOSE_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // Glucose Measurement Characteristic
        val glucoseMeasurement = BluetoothGattCharacteristic(
            GLUCOSE_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        val descriptor = BluetoothGattDescriptor(
            CLIENT_CHARACTERISTIC_CONFIG,
            BluetoothGattDescriptor.PERMISSION_WRITE
        )
        
        glucoseMeasurement.addDescriptor(descriptor)
        service.addCharacteristic(glucoseMeasurement)
        
        return service
    }
    
    private fun createCustomGlucoseService(): BluetoothGattService {
        val service = BluetoothGattService(
            CUSTOM_GLUCOSE_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        val glucoseCharacteristic = BluetoothGattCharacteristic(
            CUSTOM_GLUCOSE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        service.addCharacteristic(glucoseCharacteristic)
        return service
    }
    
    private fun startAdvertising() {
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "Bluetooth LE Advertiser not available")
            return
        }
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(ParcelUuid(GLUCOSE_SERVICE_UUID))
            .build()
        
        advertisingCallback = object : AdvertisingCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "BLE advertising started successfully")
            }
            
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE advertising failed with code: $errorCode")
            }
        }
        
        bluetoothLeAdvertiser?.startAdvertising(settings, data, advertisingCallback)
    }
    
    private fun buildGlucoseNotification(): ByteArray {
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        
        // Flags (1 byte) - indicates time offset present and glucose concentration units
        val flags: Byte = 0x01 // Time offset present, glucose in mg/dL
        buffer.put(flags)
        
        // Sequence number (2 bytes)
        buffer.putShort(sequenceNumber++.toShort())
        
        // Base time (7 bytes) - simplified to current timestamp
        val timestamp = System.currentTimeMillis()
        buffer.put((timestamp and 0xFF).toByte())
        buffer.put((timestamp shr 8 and 0xFF).toByte())
        buffer.put((timestamp shr 16 and 0xFF).toByte())
        buffer.put((timestamp shr 24 and 0xFF).toByte())
        buffer.put(0) // Year high byte
        buffer.put(0) // Month
        buffer.put(0) // Day
        
        // Glucose concentration (2 bytes) - SFLOAT format
        val glucoseSfloat = encodeGlucoseAsSfloat(currentGlucoseValue)
        buffer.putShort(glucoseSfloat)
        
        return buffer.array()
    }
    
    private fun encodeGlucoseAsSfloat(glucoseValue: Int): Short {
        // SFLOAT: 4-bit exponent, 12-bit mantissa
        // For glucose in mg/dL, we typically use exponent 0
        val mantissa = glucoseValue.coerceIn(0, 4095) // 12-bit max
        val exponent = 0
        return ((exponent shl 12) or mantissa).toShort()
    }
    
    private fun broadcastGlucoseReading() {
        val glucoseData = buildGlucoseNotification()
        
        bluetoothGattServer?.getService(GLUCOSE_SERVICE_UUID)?.let { service ->
            val characteristic = service.getCharacteristic(GLUCOSE_MEASUREMENT_UUID)
            characteristic?.value = glucoseData
            
            connectedDevices.forEach { device ->
                bluetoothGattServer?.notifyCharacteristicChanged(
                    device, characteristic, false
                )
            }
        }
        
        // Update notification
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Broadcasted glucose reading: $currentGlucoseValue mg/dL to ${connectedDevices.size} devices")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        bluetoothGattServer?.close()
        scope.cancel()
        Log.d(TAG, "Glucose GATT service destroyed")
    }
    
    private fun stopAdvertising() {
        advertisingCallback?.let {
            bluetoothLeAdvertiser?.stopAdvertising(it)
        }
        advertisingCallback = null
    }
    
    fun updateGlucoseValue(newValue: Int) {
        currentGlucoseValue = newValue
        broadcastGlucoseReading()
    }
    
    fun getConnectedDeviceCount(): Int = connectedDevices.size
}