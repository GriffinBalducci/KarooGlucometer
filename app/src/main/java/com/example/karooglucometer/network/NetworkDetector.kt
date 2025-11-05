package com.example.karooglucometer.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.NetworkInterface

/**
 * Utility to detect network connectivity type and verify Bluetooth PAN
 */
class NetworkDetector(private val context: Context) {
    
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Get current network connection type
     */
    fun getNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH_PAN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }
    }
    
    /**
     * Check if Bluetooth PAN is active
     */
    fun isBluetoothPanActive(): Boolean {
        return getNetworkType() == NetworkType.BLUETOOTH_PAN
    }
    
    /**
     * Get all network interfaces (useful for debugging)
     */
    fun getNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val interfaces = mutableListOf<NetworkInterfaceInfo>()
        
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                val addresses = networkInterface.inetAddresses?.toList()
                    ?.filter { !it.isLoopbackAddress }
                    ?.map { it.hostAddress ?: "" }
                    ?: emptyList()
                
                if (addresses.isNotEmpty()) {
                    interfaces.add(
                        NetworkInterfaceInfo(
                            name = networkInterface.name,
                            displayName = networkInterface.displayName,
                            addresses = addresses,
                            isBluetoothPan = networkInterface.name.contains("bt", ignoreCase = true) 
                                || networkInterface.name.contains("bnep", ignoreCase = true)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkDetector", "Error getting network interfaces", e)
        }
        
        return interfaces
    }
    
    /**
     * Get detailed network status for debugging
     */
    fun getNetworkStatus(): NetworkStatus {
        val networkType = getNetworkType()
        val interfaces = getNetworkInterfaces()
        val bluetoothInterface = interfaces.find { it.isBluetoothPan }
        
        return NetworkStatus(
            networkType = networkType,
            isBluetoothPanActive = networkType == NetworkType.BLUETOOTH_PAN,
            bluetoothPanIp = bluetoothInterface?.addresses?.firstOrNull(),
            allInterfaces = interfaces
        )
    }
    
    /**
     * Log current network status for debugging
     */
    fun logNetworkStatus() {
        val status = getNetworkStatus()
        Log.d("NetworkDetector", "=== Network Status ===")
        Log.d("NetworkDetector", "Network Type: ${status.networkType}")
        Log.d("NetworkDetector", "Bluetooth PAN Active: ${status.isBluetoothPanActive}")
        Log.d("NetworkDetector", "Bluetooth PAN IP: ${status.bluetoothPanIp ?: "N/A"}")
        Log.d("NetworkDetector", "All Interfaces:")
        status.allInterfaces.forEach { netInterface ->
            Log.d("NetworkDetector", "  ${netInterface.name} (${netInterface.displayName}): ${netInterface.addresses}")
            if (netInterface.isBluetoothPan) {
                Log.d("NetworkDetector", "    ^^ BLUETOOTH PAN INTERFACE ^^")
            }
        }
    }
}

enum class NetworkType {
    BLUETOOTH_PAN,
    WIFI,
    CELLULAR,
    ETHERNET,
    UNKNOWN,
    NONE
}

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val addresses: List<String>,
    val isBluetoothPan: Boolean
)

data class NetworkStatus(
    val networkType: NetworkType,
    val isBluetoothPanActive: Boolean,
    val bluetoothPanIp: String?,
    val allInterfaces: List<NetworkInterfaceInfo>
)
