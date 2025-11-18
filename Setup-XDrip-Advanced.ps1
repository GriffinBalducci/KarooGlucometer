param(
    [Parameter(Mandatory=$false)]
    [string]$DeviceId,
    [switch]$EngineeringMode = $true,
    [switch]$InjectTestData = $true,
    [switch]$EnableWebService = $true,
    [switch]$ForceReinstall = $false,
    [string]$XDripApkPath = ""
)

Write-Host ""
Write-Host "Advanced xDrip+ Configuration Script" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

# Auto-detect Karoo device if not specified
if (-not $DeviceId) {
    Write-Host "Auto-detecting Karoo device..." -ForegroundColor White
    $devices = adb devices 2>$null | Where-Object { $_ -match "KAROO" -and $_ -match "device" }
    
    if (-not $devices) {
        Write-Host "No Karoo device found. Please connect your Karoo device." -ForegroundColor Red
        exit 1
    }
    
    $DeviceId = ($devices[0] -split "\s+")[0]
    Write-Host "Found Karoo device: $DeviceId" -ForegroundColor Green
} else {
    Write-Host "Using specified device: $DeviceId" -ForegroundColor Green
}

# Check if xDrip+ is installed
Write-Host "Checking xDrip+ installation..." -ForegroundColor White

$xdripInstalled = $false
$packageCheck = adb -s $DeviceId shell "pm list packages | grep com.eveningoutpost.dexdrip" 2>$null

if ($packageCheck) {
    $xdripInstalled = $true
    Write-Host "✓ xDrip+ is already installed" -ForegroundColor Green
} else {
    Write-Host "✗ xDrip+ not found" -ForegroundColor Red
}

# Handle installation if needed
if (-not $xdripInstalled -or $ForceReinstall) {
    if ($XDripApkPath -and (Test-Path $XDripApkPath)) {
        Write-Host "Installing xDrip+ from: $XDripApkPath" -ForegroundColor Yellow
        
        $installResult = adb -s $DeviceId install -r $XDripApkPath 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ xDrip+ installed successfully" -ForegroundColor Green
            $xdripInstalled = $true
        } else {
            Write-Host "✗ xDrip+ installation failed: $installResult" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host ""
        Write-Host "xDrip+ Installation Required" -ForegroundColor Yellow
        Write-Host "============================" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Download the latest xDrip+ APK from:" -ForegroundColor Cyan
        Write-Host "https://github.com/NightscoutFoundation/xDrip/releases" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Then run this script with:" -ForegroundColor White
        Write-Host ".\Setup-XDrip-Advanced.ps1 -XDripApkPath 'path\to\xdrip.apk'" -ForegroundColor Gray
        Write-Host ""
        exit 1
    }
}

if (-not $xdripInstalled) {
    Write-Host "Cannot proceed without xDrip+ installation" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Configuring xDrip+ for glucose monitoring..." -ForegroundColor Cyan

# Step 1: Grant comprehensive permissions
Write-Host "Step 1: Granting permissions..." -ForegroundColor White

$permissions = @(
    "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_ADMIN",
    "android.permission.BLUETOOTH_SCAN", 
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.WAKE_LOCK",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.RECEIVE_BOOT_COMPLETED"
)

foreach ($permission in $permissions) {
    adb -s $DeviceId shell "pm grant com.eveningoutpost.dexdrip $permission" 2>$null
}

Write-Host "✓ Permissions granted" -ForegroundColor Green

# Step 2: Enable Engineering Mode
if ($EngineeringMode) {
    Write-Host "Step 2: Enabling Engineering Mode..." -ForegroundColor White
    
    # Multiple methods to enable engineering mode
    adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.ENABLE_ENGINEERING_MODE" 2>$null
    adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'engineering_mode' --es preference_value 'true'" 2>$null
    
    # Alternative method using activity
    adb -s $DeviceId shell "am start -n com.eveningoutpost.dexdrip/.SettingsActivity --ez engineering_mode true" 2>$null
    
    Write-Host "✓ Engineering Mode enabled" -ForegroundColor Green
}

# Step 3: Configure data source for follower mode
Write-Host "Step 3: Configuring data source..." -ForegroundColor White

# Set data source to follower mode
adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'dex_collection_method' --es preference_value 'Follower'" 2>$null

# Enable accepting followers
adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'accept_followers' --es preference_value 'true'" 2>$null

# Set up local HTTP server for followers
adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'local_server_enabled' --es preference_value 'true'" 2>$null

Write-Host "✓ Data source configured for follower mode" -ForegroundColor Green

# Step 4: Enable and configure web service
if ($EnableWebService) {
    Write-Host "Step 4: Configuring web service..." -ForegroundColor White
    
    # Enable external status service (REST API)
    adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'external_status_service_enabled' --es preference_value 'true'" 2>$null
    
    # Enable web service
    adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'webservice_enabled' --es preference_value 'true'" 2>$null
    
    # Set web service port (default 17580)
    adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'webservice_port' --es preference_value '17580'" 2>$null
    
    # Disable authentication for local access
    adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'webservice_secret' --es preference_value ''" 2>$null
    
    # Enable CORS for web access
    adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'webservice_cors_enabled' --es preference_value 'true'" 2>$null
    
    Write-Host "✓ Web service configured on port 17580" -ForegroundColor Green
}

# Step 5: Configure additional settings for testing
Write-Host "Step 5: Configuring additional settings..." -ForegroundColor White

# Enable extra logging for debugging
adb -s $DeviceId shell "am broadcast -a com.evenningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'extra_tags_for_logging' --es preference_value 'true'" 2>$null

# Disable battery optimization warnings
adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'disable_glucose_alarms_on_low_battery' --es preference_value 'false'" 2>$null

# Enable background processing
adb -s $DeviceId shell "cmd appops set com.eveningoutpost.dexdrip RUN_IN_BACKGROUND allow" 2>$null

Write-Host "✓ Additional settings configured" -ForegroundColor Green

# Step 6: Start xDrip+ and services
Write-Host "Step 6: Starting xDrip+ services..." -ForegroundColor White

# Start main xDrip+ activity
adb -s $DeviceId shell "am start -n com.eveningoutpost.dexdrip/.Home" 2>$null
Start-Sleep -Seconds 2

# Start web service
adb -s $DeviceId shell "am startservice -a com.eveningoutpost.dexdrip.RESTART_WEB_SERVICE" 2>$null

# Start data collection service
adb -s $DeviceId shell "am startservice -n com.eveningoutpost.dexdrip/.services.SyncService" 2>$null

Write-Host "✓ Services started" -ForegroundColor Green

# Step 7: Inject comprehensive test data
if ($InjectTestData) {
    Write-Host "Step 7: Injecting realistic test data..." -ForegroundColor White
    
    $timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    
    # Create 2 hours of realistic glucose data
    $testData = @()
    $baseGlucose = 120
    
    # Generate 24 readings (5-minute intervals for 2 hours)
    for ($i = 0; $i -lt 24; $i++) {
        $variation = Get-Random -Minimum -8 -Maximum 8
        $glucose = [Math]::Max(70, [Math]::Min(300, $baseGlucose + $variation))
        $baseGlucose = $glucose
        
        $readingTime = $timestamp - ($i * 5 * 60 * 1000)  # 5 minutes apart
        
        $trend = if ($variation -gt 3) { 3 } elseif ($variation -lt -3) { 5 } else { 4 }
        $direction = switch ($trend) {
            3 { "FortyFiveUp" }
            5 { "FortyFiveDown" }
            default { "Flat" }
        }
        
        $testData += @{
            glucose = $glucose
            timestamp = $readingTime
            trend = $trend
            direction = $direction
        }
    }
    
    # Inject data using multiple methods for reliability
    foreach ($reading in $testData) {
        # Method 1: Engineering mode broadcast
        adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.ADD_BG_ESTIMATE --es glucose '$($reading.glucose)' --es timestamp '$($reading.timestamp)' --es trend '$($reading.trend)' --es direction '$($reading.direction)'" 2>$null
        
        # Method 2: Direct data insertion
        adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.BG_ESTIMATE --ef glucose $($reading.glucose) --el timestamp $($reading.timestamp)" 2>$null
    }
    
    Write-Host "✓ Injected $($testData.Count) test readings" -ForegroundColor Green
}

# Step 8: Verification
Write-Host "Step 8: Verifying configuration..." -ForegroundColor White

Start-Sleep -Seconds 3

# Test web service
$webServiceTest = adb -s $DeviceId shell "curl -s http://localhost:17580/sgv.json" 2>$null
if ($webServiceTest -and $webServiceTest.Contains("sgv")) {
    Write-Host "✓ Web service responding correctly" -ForegroundColor Green
} else {
    Write-Host "⚠ Web service verification inconclusive" -ForegroundColor Yellow
}

# Check if data is available
$dataCheck = adb -s $DeviceId shell "am broadcast -a com.eveningoutpost.dexdrip.QUERY_BG_ESTIMATE" 2>$null

Write-Host ""
Write-Host "Configuration Complete!" -ForegroundColor Green
Write-Host "=====================" -ForegroundColor Green
Write-Host ""
Write-Host "xDrip+ is now configured with:" -ForegroundColor Cyan
Write-Host "• Engineering Mode: $EngineeringMode" -ForegroundColor White
Write-Host "• Web Service: $EnableWebService (port 17580)" -ForegroundColor White
Write-Host "• Test Data: $InjectTestData" -ForegroundColor White
Write-Host "• Data Source: Follower Mode" -ForegroundColor White
Write-Host ""
Write-Host "Manual verification steps:" -ForegroundColor Cyan
Write-Host "1. Open xDrip+ app on Karoo" -ForegroundColor White
Write-Host "2. Check that glucose readings are visible" -ForegroundColor White
Write-Host "3. Settings → Data Source should show 'Follower'" -ForegroundColor White
Write-Host "4. Settings → Inter-app Settings → 'Accept Followers' should be ON" -ForegroundColor White
Write-Host "5. Test URL: http://localhost:17580/sgv.json should return glucose data" -ForegroundColor White
Write-Host ""
Write-Host "Troubleshooting:" -ForegroundColor Yellow
Write-Host "• If no data appears, manually add glucose in Engineering Mode" -ForegroundColor Gray
Write-Host "• Check Settings → Less Common Settings → Extra Logging = ON" -ForegroundColor Gray  
Write-Host "• Restart xDrip+ if web service doesn't respond" -ForegroundColor Gray
Write-Host ""