package com.example.karooglucometer.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Utility to test basic network connectivity before attempting HTTP requests
 */
class ConnectionTester {
    private val TAG = "ConnectionTester"
    
    /**
     * Test if a host:port is reachable
     * @param host IP address or hostname
     * @param port Port number
     * @param timeoutMs Connection timeout in milliseconds
     * @return ConnectionTestResult with details
     */
    suspend fun testConnection(
        host: String,
        port: Int = 17580,
        timeoutMs: Int = 3000
    ): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                Log.d(TAG, "Testing connection to $host:$port (timeout: ${timeoutMs}ms)")
                
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    val latency = System.currentTimeMillis() - startTime
                    
                    Log.d(TAG, "[PASS] Connection successful to $host:$port (${latency}ms)")
                    
                    ConnectionTestResult(
                        success = true,
                        host = host,
                        port = port,
                        latencyMs = latency,
                        errorMessage = null
                    )
                }
            } catch (e: java.net.SocketTimeoutException) {
                val latency = System.currentTimeMillis() - startTime
                Log.e(TAG, "✗ Connection timeout to $host:$port after ${latency}ms")
                ConnectionTestResult(
                    success = false,
                    host = host,
                    port = port,
                    latencyMs = latency,
                    errorMessage = "Connection timeout - xDrip service may not be running or wrong IP"
                )
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "✗ Connection refused to $host:$port - ${e.message}")
                ConnectionTestResult(
                    success = false,
                    host = host,
                    port = port,
                    latencyMs = System.currentTimeMillis() - startTime,
                    errorMessage = "Connection refused - check IP address and xDrip web service"
                )
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "✗ Unknown host: $host")
                ConnectionTestResult(
                    success = false,
                    host = host,
                    port = port,
                    latencyMs = System.currentTimeMillis() - startTime,
                    errorMessage = "Invalid IP address: $host"
                )
            } catch (e: Exception) {
                Log.e(TAG, "✗ Connection test failed: ${e.message}", e)
                ConnectionTestResult(
                    success = false,
                    host = host,
                    port = port,
                    latencyMs = System.currentTimeMillis() - startTime,
                    errorMessage = "Connection failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Test connection and log detailed diagnostics
     */
    suspend fun testAndLog(host: String, port: Int = 17580): Boolean {
        val result = testConnection(host, port)
        
        Log.d(TAG, "=== Connection Test Results ===")
        Log.d(TAG, "Host: ${result.host}:${result.port}")
        Log.d(TAG, "Success: ${result.success}")
        Log.d(TAG, "Latency: ${result.latencyMs}ms")
        if (result.errorMessage != null) {
            Log.d(TAG, "Error: ${result.errorMessage}")
        }
        
        return result.success
    }
}

data class ConnectionTestResult(
    val success: Boolean,
    val host: String,
    val port: Int,
    val latencyMs: Long,
    val errorMessage: String?
)
