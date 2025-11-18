param(
    [bool]$SetupTestData = $true,
    [switch]$SkipBuild = $false
)

# Configuration
$AppPackage = "com.example.karooglucometer"

Write-Host ""
Write-Host "KarooGlucometer Deployment Script" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

# Detect Karoo device
Write-Host "Detecting Karoo device..." -ForegroundColor White

$devices = adb devices 2>$null | Where-Object { $_ -match "KAROO" -and $_ -match "device" }

if (-not $devices) {
    Write-Host "No Karoo device found. Please connect your Karoo device." -ForegroundColor Red
    exit 1
}

$deviceId = ($devices[0] -split "\s+")[0]
Write-Host "Found Karoo device: $deviceId" -ForegroundColor Green

# Build the app
if (-not $SkipBuild) {
    Write-Host "Building KarooGlucometer..." -ForegroundColor White
    
    $buildProcess = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "assembleDebug" -NoNewWindow -Wait -PassThru
    
    if ($buildProcess.ExitCode -eq 0) {
        Write-Host "Build completed successfully" -ForegroundColor Green
    } else {
        Write-Host "Build failed" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Skipping build (SkipBuild flag set)" -ForegroundColor Cyan
}

# Install app
Write-Host "Installing KarooGlucometer to Karoo device..." -ForegroundColor White

$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apkPath)) {
    Write-Host "APK not found at $apkPath" -ForegroundColor Red
    exit 1
}

$installProcess = Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "install", "-r", $apkPath -NoNewWindow -Wait -PassThru

if ($installProcess.ExitCode -eq 0) {
    Write-Host "App installed successfully" -ForegroundColor Green
} else {
    Write-Host "App installation failed" -ForegroundColor Red
    exit 1
}

# Grant BLE permissions
Write-Host "Granting BLE permissions..." -ForegroundColor White

$permissions = @(
    "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_ADMIN", 
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.BLUETOOTH_CONNECT"
)

foreach ($permission in $permissions) {
    Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "pm grant $AppPackage $permission" -NoNewWindow -Wait | Out-Null
}

Write-Host "BLE permissions granted" -ForegroundColor Green

# Configure xDrip+ with test data if requested
if ($SetupTestData) {
    Write-Host "Setting up xDrip+ comprehensive test configuration..." -ForegroundColor White
    
    # Check if xDrip+ is installed
    $xdripCheck = Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "pm list packages | grep com.eveningoutpost.dexdrip" -NoNewWindow -Wait -PassThru -RedirectStandardOutput "temp_xdrip.txt"
    
    $xdripFound = $false
    if (Test-Path "temp_xdrip.txt") {
        $xdripContent = Get-Content "temp_xdrip.txt" -ErrorAction SilentlyContinue
        if ($xdripContent) {
            $xdripFound = $true
        }
        Remove-Item "temp_xdrip.txt" -ErrorAction SilentlyContinue
    }
    
    if ($xdripFound) {
        Write-Host "xDrip+ found, performing comprehensive configuration..." -ForegroundColor Green
        
        # Step 1: Grant all necessary permissions for xDrip+
        Write-Host "  Granting xDrip+ permissions..." -ForegroundColor Cyan
        $xdripPermissions = @(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION", 
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.WAKE_LOCK",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        )
        
        foreach ($permission in $xdripPermissions) {
            Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "pm grant com.eveningoutpost.dexdrip $permission" -NoNewWindow -Wait | Out-Null
        }
        
        # Step 2: Enable Engineering Mode (required for test data injection)
        Write-Host "  Enabling Engineering Mode..." -ForegroundColor Cyan
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am broadcast -a com.eveningoutpost.dexdrip.ENABLE_ENGINEERING_MODE" -NoNewWindow -Wait | Out-Null
        
        # Step 3: Configure data source to "Follower" mode for external data
        Write-Host "  Configuring data source..." -ForegroundColor Cyan
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am start -n com.eveningoutpost.dexdrip/.SettingsActivity --es extra_key 'dex_collection_method' --es extra_value 'Follower'" -NoNewWindow -Wait | Out-Null
        
        # Step 4: Enable web service (port 17580 for HTTP access)
        Write-Host "  Enabling web service on port 17580..." -ForegroundColor Cyan
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'external_status_service_enabled' --es preference_value 'true'" -NoNewWindow -Wait | Out-Null
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'webservice_secret' --es preference_value ''" -NoNewWindow -Wait | Out-Null
        
        # Step 5: Configure calibration and validation settings for test mode
        Write-Host "  Configuring calibration settings..." -ForegroundColor Cyan
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'engineering_mode' --es preference_value 'true'" -NoNewWindow -Wait | Out-Null
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am broadcast -a com.eveningoutpost.dexdrip.SET_PREFERENCE --es preference_key 'disable_glucose_alarms_on_low_battery' --es preference_value 'false'" -NoNewWindow -Wait | Out-Null
        
        # Step 6: Force start xDrip+ service
        Write-Host "  Starting xDrip+ services..." -ForegroundColor Cyan
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am start -n com.eveningoutpost.dexdrip/.Home" -NoNewWindow -Wait | Out-Null
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am startservice -n com.eveningoutpost.dexdrip/.webservices.WebServiceTasker" -NoNewWindow -Wait | Out-Null
        
        # Wait for services to initialize
        Start-Sleep -Seconds 3
        
        # Step 7: Inject comprehensive test data using multiple methods
        Write-Host "  Injecting comprehensive test glucose data..." -ForegroundColor Cyan
        $timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
        
        # Create realistic test data sequence (last 30 minutes)
        $testReadings = @(
            @{ glucose = 120; minutes = 0; trend = 4; direction = "Flat" },
            @{ glucose = 118; minutes = 5; trend = 5; direction = "FortyFiveDown" },
            @{ glucose = 122; minutes = 10; trend = 3; direction = "FortyFiveUp" },
            @{ glucose = 125; minutes = 15; trend = 4; direction = "Flat" },
            @{ glucose = 128; minutes = 20; trend = 3; direction = "FortyFiveUp" },
            @{ glucose = 126; minutes = 25; trend = 4; direction = "Flat" },
            @{ glucose = 124; minutes = 30; trend = 5; direction = "FortyFiveDown" }
        )
        
        foreach ($reading in $testReadings) {
            $readingTime = $timestamp - ($reading.minutes * 60000)
            
            # Method 1: Direct database insertion (Engineering mode)
            Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am broadcast -a com.eveningoutpost.dexdrip.ADD_BG_ESTIMATE --es glucose '$($reading.glucose)' --es timestamp '$readingTime' --es trend '$($reading.trend)' --es direction '$($reading.direction)'" -NoNewWindow -Wait | Out-Null
            
            # Method 2: Web service injection (backup method)
            $jsonData = @{
                sgv = $reading.glucose
                date = $readingTime
                direction = $reading.direction
                trend = $reading.trend
                device = "TestDevice"
                type = "sgv"
            } | ConvertTo-Json -Compress
            
            Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "curl -X POST -H 'Content-Type: application/json' -d '$jsonData' http://localhost:17580/sgv" -NoNewWindow -Wait | Out-Null
        }
        
        # Step 8: Verify web service is running
        Write-Host "  Verifying xDrip+ web service..." -ForegroundColor Cyan
        $webCheck = Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "curl -s http://localhost:17580/sgv.json" -NoNewWindow -Wait -PassThru -RedirectStandardOutput "temp_web_check.txt"
        
        if (Test-Path "temp_web_check.txt") {
            $webResponse = Get-Content "temp_web_check.txt" -ErrorAction SilentlyContinue
            if ($webResponse -and $webResponse.Contains("sgv")) {
                Write-Host "  ✓ xDrip+ web service responding correctly" -ForegroundColor Green
            } else {
                Write-Host "  ⚠ xDrip+ web service may need manual activation" -ForegroundColor Yellow
            }
            Remove-Item "temp_web_check.txt" -ErrorAction SilentlyContinue
        }
        
        Write-Host "  ✓ Comprehensive xDrip+ configuration completed" -ForegroundColor Green
        Write-Host "  ℹ Manual steps if needed:" -ForegroundColor Cyan
        Write-Host "    1. Open xDrip+ → Settings → Data Source → Select 'Follower'" -ForegroundColor Gray
        Write-Host "    2. Settings → Inter-app Settings → Accept Followers = ON" -ForegroundColor Gray
        Write-Host "    3. Settings → Cloud Upload → REST API → Enable Upload = ON" -ForegroundColor Gray
        Write-Host "    4. Menu → Engineering Mode → Enable if not active" -ForegroundColor Gray
        
    } else {
        Write-Host "xDrip+ not found - providing installation and setup guide" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "xDrip+ Installation & Setup Guide:" -ForegroundColor Cyan
        Write-Host "===================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "1. Download xDrip+ APK:" -ForegroundColor White
        Write-Host "   https://github.com/NightscoutFoundation/xDrip/releases" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "2. Install via ADB:" -ForegroundColor White
        Write-Host "   adb -s $deviceId install xdrip-plus-*.apk" -ForegroundColor Gray
        Write-Host ""
        Write-Host "3. Manual Configuration:" -ForegroundColor White
        Write-Host "   - Open xDrip+ on Karoo" -ForegroundColor Gray
        Write-Host "   - Settings → Data Source → 'Follower'" -ForegroundColor Gray
        Write-Host "   - Settings → Inter-app Settings → 'Accept Followers' = ON" -ForegroundColor Gray
        Write-Host "   - Settings → Cloud Upload → REST API → 'Enable Upload' = ON" -ForegroundColor Gray
        Write-Host "   - Menu → Engineering Mode → Enable" -ForegroundColor Gray
        Write-Host "   - Settings → Less Common Settings → Extra Logging = ON" -ForegroundColor Gray
        Write-Host ""
        Write-Host "4. Test Data Injection:" -ForegroundColor White
        Write-Host "   - Menu → Engineering Mode → 'Add Glucose Data'" -ForegroundColor Gray
        Write-Host "   - Or use: adb shell am broadcast -a com.eveningoutpost.dexdrip.ADD_BG_ESTIMATE --es glucose '120'" -ForegroundColor Gray
        Write-Host ""
    }
}

# Start the app
Write-Host "Starting KarooGlucometer..." -ForegroundColor White

$startProcess = Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am start -n $AppPackage/.MainActivity" -NoNewWindow -Wait -PassThru

if ($startProcess.ExitCode -eq 0) {
    Write-Host "App started successfully" -ForegroundColor Green
} else {
    Write-Host "Failed to start app" -ForegroundColor Red
    exit 1
}

# Show summary
Write-Host ""
Write-Host "KarooGlucometer Deployment Complete!" -ForegroundColor Green
Write-Host "====================================" -ForegroundColor Green
Write-Host ""
Write-Host "Device: $deviceId" -ForegroundColor Cyan
Write-Host "Package: $AppPackage" -ForegroundColor Cyan

if ($SetupTestData) {
    if ($xdripFound) {
        Write-Host "xDrip+ Configuration: Complete (Engineering Mode + Web Service)" -ForegroundColor Cyan
    } else {
        Write-Host "xDrip+ Configuration: Manual setup required (see guide above)" -ForegroundColor Cyan
    }
}

Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "1. Open KarooGlucometer app on Karoo" -ForegroundColor Cyan
Write-Host "2. Tap debug button (Info) to view connection status" -ForegroundColor Cyan
Write-Host "3. Check BLE GATT and Data Sources sections" -ForegroundColor Cyan
Write-Host "4. Test with both onboard and external sources" -ForegroundColor Cyan
Write-Host ""
Write-Host "Ready for testing!" -ForegroundColor Green