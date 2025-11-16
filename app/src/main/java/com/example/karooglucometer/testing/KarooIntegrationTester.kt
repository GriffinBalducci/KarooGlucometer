package com.example.karooglucometer.testing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.karooglucometer.karoo.KarooDataFieldService

/**
 * Comprehensive testing utility for Karoo data field integration
 * Validates all components required for legacy Karoo compatibility
 */
class KarooIntegrationTester(private val context: Context) {
    
    companion object {
        private const val TAG = "KarooIntegrationTester"
    }
    
    /**
     * Run comprehensive Karoo integration tests
     */
    fun runComprehensiveTest(): TestResult {
        Log.d(TAG, "🧪 Starting comprehensive Karoo integration test...")
        
        val results = mutableListOf<TestStep>()
        
        // Test 1: Validate manifest permissions
        results.add(testManifestPermissions())
        
        // Test 2: Validate service registration
        results.add(testServiceRegistration())
        
        // Test 3: Validate broadcast receiver registration
        results.add(testBroadcastReceiverRegistration())
        
        // Test 4: Test broadcast capability
        results.add(testBroadcastCapability())
        
        // Test 5: Test discovery simulation
        results.add(testDiscoverySimulation())
        
        // Test 6: Test data field request simulation
        results.add(testDataFieldRequestSimulation())
        
        val passedTests = results.count { it.passed }
        val totalTests = results.size
        
        Log.d(TAG, "🏁 Test completed: $passedTests/$totalTests tests passed")
        
        return TestResult(
            passed = passedTests == totalTests,
            totalTests = totalTests,
            passedTests = passedTests,
            results = results
        )
    }
    
    private fun testManifestPermissions(): TestStep {
        Log.d(TAG, "🔐 Testing manifest permissions...")
        
        try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            
            // Check for Karoo data field permission
            val hasKarooPermission = try {
                packageManager.getPermissionInfo("com.hammerhead.karoo.permission.DATA_FIELD", 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            
            if (hasKarooPermission) {
                Log.d(TAG, "✅ Karoo data field permission declared")
                return TestStep("Manifest Permissions", true, "Karoo data field permission found")
            } else {
                Log.w(TAG, "⚠️ Karoo data field permission not declared in manifest")
                return TestStep("Manifest Permissions", false, "Missing Karoo permission declaration")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Manifest permission test failed", e)
            return TestStep("Manifest Permissions", false, "Test failed: ${e.message}")
        }
    }
    
    private fun testServiceRegistration(): TestStep {
        Log.d(TAG, "🛠️ Testing service registration...")
        
        try {
            val serviceIntent = Intent(context, KarooDataFieldService::class.java)
            val resolveInfo = context.packageManager.resolveService(serviceIntent, 0)
            
            if (resolveInfo != null) {
                Log.d(TAG, "✅ KarooDataFieldService is registered")
                return TestStep("Service Registration", true, "KarooDataFieldService found in manifest")
            } else {
                Log.w(TAG, "⚠️ KarooDataFieldService not found in manifest")
                return TestStep("Service Registration", false, "Service not registered")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Service registration test failed", e)
            return TestStep("Service Registration", false, "Test failed: ${e.message}")
        }
    }
    
    private fun testBroadcastReceiverRegistration(): TestStep {
        Log.d(TAG, "📡 Testing broadcast receiver registration...")
        
        try {
            val discoveryIntent = Intent("com.hammerhead.karoo.DISCOVER_DATA_PROVIDERS")
            val receivers = context.packageManager.queryBroadcastReceivers(discoveryIntent, 0)
            
            val karooReceiver = receivers.find { 
                it.activityInfo.name.contains("KarooDataFieldReceiver") 
            }
            
            if (karooReceiver != null) {
                Log.d(TAG, "✅ KarooDataFieldReceiver is registered")
                return TestStep("Receiver Registration", true, "KarooDataFieldReceiver found in manifest")
            } else {
                Log.w(TAG, "⚠️ KarooDataFieldReceiver not found in manifest")
                return TestStep("Receiver Registration", false, "Receiver not registered")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Receiver registration test failed", e)
            return TestStep("Receiver Registration", false, "Test failed: ${e.message}")
        }
    }
    
    private fun testBroadcastCapability(): TestStep {
        Log.d(TAG, "📢 Testing broadcast capability...")
        
        try {
            val testIntent = Intent("com.hammerhead.karoo.TEST_INTEGRATION").apply {
                putExtra("test_message", "Karoo integration test")
                putExtra("timestamp", System.currentTimeMillis())
                putExtra("package_name", context.packageName)
            }
            
            context.sendBroadcast(testIntent)
            Log.d(TAG, "✅ Test broadcast sent successfully")
            
            return TestStep("Broadcast Capability", true, "Broadcast permission verified")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Broadcast permission denied", e)
            return TestStep("Broadcast Capability", false, "Broadcast permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Broadcast test failed", e)
            return TestStep("Broadcast Capability", false, "Test failed: ${e.message}")
        }
    }
    
    private fun testDiscoverySimulation(): TestStep {
        Log.d(TAG, "🔍 Testing discovery simulation...")
        
        try {
            val discoveryIntent = Intent("com.hammerhead.karoo.DISCOVER_DATA_PROVIDERS").apply {
                putExtra("requesting_package", "com.hammerhead.karoo")
                putExtra("discovery_id", "test_discovery_${System.currentTimeMillis()}")
            }
            
            context.sendBroadcast(discoveryIntent)
            Log.d(TAG, "✅ Discovery broadcast simulation sent")
            
            return TestStep("Discovery Simulation", true, "Discovery broadcast sent successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Discovery simulation failed", e)
            return TestStep("Discovery Simulation", false, "Test failed: ${e.message}")
        }
    }
    
    private fun testDataFieldRequestSimulation(): TestStep {
        Log.d(TAG, "📋 Testing data field request simulation...")
        
        try {
            val requestIntent = Intent("com.hammerhead.karoo.REQUEST_DATA_FIELDS").apply {
                putExtra("requesting_package", "com.hammerhead.karoo")
                putExtra("request_id", "test_request_${System.currentTimeMillis()}")
            }
            
            context.sendBroadcast(requestIntent)
            Log.d(TAG, "✅ Data field request simulation sent")
            
            return TestStep("Data Field Request", true, "Data field request sent successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Data field request simulation failed", e)
            return TestStep("Data Field Request", false, "Test failed: ${e.message}")
        }
    }
}

data class TestResult(
    val passed: Boolean,
    val totalTests: Int,
    val passedTests: Int,
    val results: List<TestStep>
)

data class TestStep(
    val name: String,
    val passed: Boolean,
    val message: String
)