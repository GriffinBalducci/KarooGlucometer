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

        private val BROADCAST_UUID: UUID =
            UUID.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
        private const val TAG = "GlucoseGattService"
        private const val NOTIFICATION_ID = 1
        private var bluetoothAdapter: BluetoothAdapter? = null
        private var advertiser: BluetoothLeAdvertiser? = null
        private var advertiseCallback: AdvertiseCallback? = null
        private const val CHANNEL_ID = "glucose_broadcaster_channel"

        // Standard Bluetooth SIG UUIDs for G  lucose Service
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
    private var advertisingCallback: AdvertiseCallback? = null

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
        createNotificationChannel()

        // ✅ no more GATT server
        // setupGattServer()

        startForeground(NOTIFICATION_ID, createNotification())

        // if you haven’t yet, this is a good place to init your advertiser:
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
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
    fun broadcastGlucose(glucoseValue: Int) {
        val adapter = bluetoothAdapter ?: return
        val adv = advertiser ?: return
        if (!adapter.isEnabled) return

        // Clamp value to 0–255 just in case
        val clamped = glucoseValue.coerceIn(0, 255)
        val payload = byteArrayOf(clamped.toByte())

        // Stop previous advertising, if any
        advertiseCallback?.let { adv.stopAdvertising(it) }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false) // IMPORTANT
            .build()

        val data = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(BROADCAST_UUID), payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "Advertising glucose=$clamped")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed: $errorCode")
            }
        }

        adv.startAdvertising(settings, data, advertiseCallback)
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
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
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

        advertisingCallback = object : AdvertiseCallback() {
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
        // Broadcast using advertisement instead of GATT
        broadcastGlucose(currentGlucoseValue)

        // Keep your foreground notification alive (optional)
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "Broadcasted glucose reading via ADV: $currentGlucoseValue mg/dL")
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