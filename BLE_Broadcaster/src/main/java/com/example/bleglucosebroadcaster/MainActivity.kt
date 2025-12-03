package com.example.bleglucosebroadcaster

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.bleglucosebroadcaster.ui.theme.BLEGlucoseBroadcasterTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var glucoseService: GlucoseGattService? = null
    private var isServiceBound = false
    private var xDripReceiver: XDripReceiver? = null

    private var currentGlucose = mutableStateOf(120)
    private var isAdvertising = mutableStateOf(false)
    private var connectionCount = mutableStateOf(0)
    private var lastUpdate = mutableStateOf("--")
    private var isXDripConnected = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeBluetooth()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        requestBluetoothPermissions()
        setupXDripReceiver()

        setContent {
            BLEGlucoseBroadcasterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GlucoseBroadcasterScreen()
                }
            }
        }
    }

    private fun setupXDripReceiver() {
        xDripReceiver = XDripReceiver()
        XDripReceiver.setListener(object : XDripReceiver.GlucoseUpdateListener {
            override fun onGlucoseUpdate(glucose: Int, timestamp: Long, delta: String?) {
                currentGlucose.value = glucose
                isXDripConnected.value = true

                // Format timestamp
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                lastUpdate.value = sdf.format(Date(timestamp))

                // Update service if running
                if (isAdvertising.value) {
                    val intent = Intent(this@MainActivity, GlucoseGattService::class.java)
                    intent.putExtra("update_glucose", glucose)
                    startService(intent)
                }

                Log.d("MainActivity", "Updated glucose from xDrip: $glucose mg/dL, delta: $delta")
            }
        })

        val intentFilter = android.content.IntentFilter().apply {
            addAction("com.eveningoutpost.dexdrip.BgEstimate")
            addAction("com.eveningoutpost.dexdrip.NS_EMULATOR")
            addAction("com.eveningoutpost.dexdrip.LAST_BG")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(xDripReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(xDripReceiver, intentFilter)
        }
    }
    @Composable
    fun GlucoseBroadcasterScreen() {
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BLE Glucose Broadcaster",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // xDrip status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isXDripConnected.value)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isXDripConnected.value) "xDrip+ Connected" else "Waiting for xDrip+",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Current Glucose",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${currentGlucose.value} mg/dL",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = getGlucoseColor(currentGlucose.value)
                    )
                    if (isXDripConnected.value) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last update: ${lastUpdate.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Manual adjustment buttons (for testing)
            Text(
                text = "Manual Adjustment (Testing)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { adjustGlucose(-10) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("-10")
                }

                Button(
                    onClick = { adjustGlucose(10) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("+10")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { toggleAdvertising() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdvertising.value)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isAdvertising.value) "Stop Broadcasting" else "Start Broadcasting",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isAdvertising.value)
                    "Broadcasting glucose data via BLE"
                else "Not broadcasting",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isAdvertising.value)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isAdvertising.value) {
                Text(
                    text = "Connected devices: ${connectionCount.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun getGlucoseColor(glucose: Int) = when {
        glucose < 70 -> MaterialTheme.colorScheme.error
        glucose > 180 -> MaterialTheme.colorScheme.error
        glucose < 80 -> MaterialTheme.colorScheme.tertiary
        glucose > 160 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            initializeBluetooth()
        }
    }

    private fun initializeBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show()
            return
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_LONG).show()
            return
        }
    }

    private fun toggleAdvertising() {
        if (isAdvertising.value) {
            stopGattService()
        } else {
            startGattService()
        }
    }

    private fun startGattService() {
        val intent = Intent(this, GlucoseGattService::class.java)
        intent.putExtra("glucose_value", currentGlucose.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isAdvertising.value = true
    }

    private fun stopGattService() {
        val intent = Intent(this, GlucoseGattService::class.java)
        stopService(intent)
        isAdvertising.value = false
        connectionCount.value = 0
    }

    private fun adjustGlucose(delta: Int) {
        val newValue = (currentGlucose.value + delta).coerceIn(40, 400)
        currentGlucose.value = newValue

        // Mark as manual adjustment (optional - resets xDrip connection indicator)
        // isXDripConnected.value = false

        // Update service if running
        if (isAdvertising.value) {
            val intent = Intent(this, GlucoseGattService::class.java)
            intent.putExtra("update_glucose", newValue)
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        xDripReceiver?.let {
            XDripReceiver.setListener(null)
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error unregistering receiver", e)
            }
        }
        stopGattService()
    }
}