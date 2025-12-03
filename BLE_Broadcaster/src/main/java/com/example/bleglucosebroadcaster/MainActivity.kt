package com.example.bleglucosebroadcaster

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.bleglucosebroadcaster.ui.theme.BLEGlucoseBroadcasterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var glucoseService: GlucoseGattService? = null
    private var isServiceBound = false
    
    private var currentGlucose = mutableStateOf(120)
    private var isAdvertising = mutableStateOf(false)
    private var connectionCount = mutableStateOf(0)
    
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
        
        // Start glucose simulation
        lifecycleScope.launch {
            while (isActive) {
                simulateGlucoseReading()
                delay(30000) // Update every 30 seconds
            }
        }
        
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
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
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
                    "Broadcasting glucose data via BLE GATT" 
                else "Not broadcasting",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isAdvertising.value) 
                    MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Connected devices: ${connectionCount.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        startForegroundService(intent)
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
        
        // Update service if running
        if (isAdvertising.value) {
            val intent = Intent(this, GlucoseGattService::class.java)
            intent.putExtra("update_glucose", newValue)
            startService(intent)
        }
    }
    
    private fun simulateGlucoseReading() {
        if (isAdvertising.value) {
            // Small random variation (-3 to +3)
            val variation = (-3..3).random()
            adjustGlucose(variation)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopGattService()
    }
}