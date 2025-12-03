param(
    [switch]$SkipBuild = $false,
    [switch]$InstallToAllDevices = $false
)

# Configuration
$AppPackage = "com.example.bleglucosebroadcaster"
$AppName = "BLE Glucose Broadcaster"

Write-Host ""
Write-Host "BLE Glucose Broadcaster Deployment Script" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will build and install the BLE Glucose Broadcaster app" -ForegroundColor White
Write-Host "to your Android phone. This app simulates a glucose monitor that" -ForegroundColor White
Write-Host "the Karoo can connect to via Bluetooth." -ForegroundColor White
Write-Host ""

# Auto-configure ADB if not available
function Install-ADB {
    Write-Host "ADB not found. Setting up Android SDK Platform Tools..." -ForegroundColor Yellow
    Write-Host ""
    
    # Create tools directory
    $toolsDir = "$env:USERPROFILE\android-tools"
    $adbDir = "$toolsDir\platform-tools"
    
    if (-not (Test-Path $toolsDir)) {
        New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    }
    
    # Download Android SDK Platform Tools if not already present
    if (-not (Test-Path "$adbDir\adb.exe")) {
        Write-Host "Downloading Android SDK Platform Tools..." -ForegroundColor White
        
        $platformToolsUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
        $zipFile = "$toolsDir\platform-tools.zip"
        
        try {
            # Download with progress
            $webClient = New-Object System.Net.WebClient
            $webClient.DownloadFile($platformToolsUrl, $zipFile)
            
            # Extract
            Write-Host "Extracting Platform Tools..." -ForegroundColor White
            Expand-Archive -Path $zipFile -DestinationPath $toolsDir -Force
            Remove-Item $zipFile -Force
            
            Write-Host "Platform Tools installed to: $adbDir" -ForegroundColor Green
        }
        catch {
            Write-Host "Failed to download Platform Tools: $($_.Exception.Message)" -ForegroundColor Red
            Write-Host "Please manually install from: https://developer.android.com/studio/releases/platform-tools" -ForegroundColor Cyan
            return $false
        }
    }
    
    # Add to PATH for this session
    $env:PATH = "$adbDir;$env:PATH"
    
    # Add to user PATH permanently
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($userPath -notlike "*$adbDir*") {
        Write-Host "Adding ADB to user PATH..." -ForegroundColor White
        [Environment]::SetEnvironmentVariable("Path", "$userPath;$adbDir", "User")
    }
    
    return $true
}

# Check if ADB is available, auto-install if needed
$adbPath = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbPath) {
    if (-not (Install-ADB)) {
        exit 1
    }
    
    # Verify installation
    $adbPath = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adbPath) {
        Write-Host "ADB installation failed. Please restart PowerShell and try again." -ForegroundColor Red
        exit 1
    }
    
    Write-Host "ADB successfully configured!" -ForegroundColor Green
    Write-Host ""
}

# Detect Android devices
Write-Host "Detecting Android devices..." -ForegroundColor White

$devices = adb devices 2>$null | Where-Object { $_ -match "device$" -and $_ -notmatch "List of devices" }

if (-not $devices) {
    Write-Host ""
    Write-Host "No Android devices found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please follow these steps:" -ForegroundColor Yellow
    Write-Host "1. Connect your Android phone via USB" -ForegroundColor White
    Write-Host "2. Enable 'Developer Options' on your phone:" -ForegroundColor White
    Write-Host "   - Go to Settings → About Phone" -ForegroundColor Gray
    Write-Host "   - Tap 'Build Number' 7 times" -ForegroundColor Gray
    Write-Host "3. Enable 'USB Debugging' in Developer Options" -ForegroundColor White
    Write-Host "4. Accept the USB debugging prompt on your phone" -ForegroundColor White
    Write-Host "5. Run this script again" -ForegroundColor White
    Write-Host ""
    exit 1
}

# Show detected devices
Write-Host "Found Android devices:" -ForegroundColor Green
$deviceList = @()
foreach ($device in $devices) {
    $deviceId = ($device -split "\s+")[0]
    $deviceName = adb -s $deviceId shell getprop ro.product.model 2>$null
    if (-not $deviceName) { $deviceName = "Unknown Device" }
    
    $deviceInfo = @{
        Id = $deviceId
        Name = $deviceName.Trim()
    }
    $deviceList += $deviceInfo
    
    Write-Host "  - $($deviceInfo.Name) ($($deviceInfo.Id))" -ForegroundColor Cyan
}

# Select device if multiple found
$selectedDevice = $null
if ($deviceList.Count -eq 1) {
    $selectedDevice = $deviceList[0]
    Write-Host "Using device: $($selectedDevice.Name)" -ForegroundColor Green
} elseif ($InstallToAllDevices) {
    Write-Host "Installing to all devices..." -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "Multiple devices found. Please select one:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $deviceList.Count; $i++) {
        Write-Host "  $($i + 1). $($deviceList[$i].Name) ($($deviceList[$i].Id))" -ForegroundColor White
    }
    Write-Host "  A. Install to all devices" -ForegroundColor White
    
    do {
        $choice = Read-Host "Enter choice (1-$($deviceList.Count) or A)"
        if ($choice -eq "A" -or $choice -eq "a") {
            $InstallToAllDevices = $true
            break
        } elseif ([int]$choice -ge 1 -and [int]$choice -le $deviceList.Count) {
            $selectedDevice = $deviceList[[int]$choice - 1]
            break
        } else {
            Write-Host "Invalid choice. Please try again." -ForegroundColor Red
        }
    } while ($true)
}

# Build the app
if (-not $SkipBuild) {
    Write-Host ""
    Write-Host "Building BLE Glucose Broadcaster..." -ForegroundColor White
    
    Push-Location BLE_Broadcaster
    
    # Check if gradlew exists
    if (-not (Test-Path "gradlew.bat")) {
        Write-Host "Gradle wrapper not found. Initializing..." -ForegroundColor Yellow
        
        # Create gradle wrapper
        if (Get-Command gradle -ErrorAction SilentlyContinue) {
            gradle wrapper
        } else {
            Write-Host "Gradle not found. Please install Gradle or Android Studio." -ForegroundColor Red
            Pop-Location
            exit 1
        }
    }
    
    $buildProcess = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "assembleDebug" -NoNewWindow -Wait -PassThru
    
    Pop-Location
    
    if ($buildProcess.ExitCode -eq 0) {
        Write-Host "✓ Build completed successfully" -ForegroundColor Green
    } else {
        Write-Host "✗ Build failed" -ForegroundColor Red
        Write-Host ""
        Write-Host "Common solutions:" -ForegroundColor Yellow
        Write-Host "1. Install Android Studio: https://developer.android.com/studio" -ForegroundColor Gray
        Write-Host "2. Set ANDROID_HOME environment variable" -ForegroundColor Gray
        Write-Host "3. Ensure Java 8+ is installed" -ForegroundColor Gray
        exit 1
    }
} else {
    Write-Host "Skipping build (SkipBuild flag set)" -ForegroundColor Cyan
}

# Find the APK
$apkPath = "BLE_Broadcaster\app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apkPath)) {
    Write-Host "APK not found at $apkPath" -ForegroundColor Red
    Write-Host "Please build the app first or check the build output." -ForegroundColor Yellow
    exit 1
}

# Installation function
function Install-ToDevice($deviceId, $deviceName) {
    Write-Host ""
    Write-Host "Installing to $deviceName..." -ForegroundColor White
    
    # Check if app is already installed
    $existingApp = adb -s $deviceId shell pm list packages | Select-String $AppPackage
    if ($existingApp) {
        Write-Host "  Updating existing installation..." -ForegroundColor Yellow
    }
    
    $installProcess = Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "install", "-r", $apkPath -NoNewWindow -Wait -PassThru
    
    if ($installProcess.ExitCode -eq 0) {
        Write-Host "  ✓ App installed successfully" -ForegroundColor Green
        
        # Grant necessary permissions
        Write-Host "  Granting Bluetooth permissions..." -ForegroundColor Cyan
        $permissions = @(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"
        )
        
        # Android 12+ permissions
        $androidVersion = adb -s $deviceId shell getprop ro.build.version.sdk 2>$null
        if ([int]$androidVersion -ge 31) {
            $permissions += @(
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN"
            )
        }
        
        foreach ($permission in $permissions) {
            Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "pm grant $AppPackage $permission" -NoNewWindow -Wait | Out-Null
        }
        
        Write-Host "  ✓ Permissions granted" -ForegroundColor Green
        
        # Launch the app
        Start-Process -FilePath "adb" -ArgumentList "-s", $deviceId, "shell", "am start -n $AppPackage/.MainActivity" -NoNewWindow -Wait | Out-Null
        Write-Host "  ✓ App launched" -ForegroundColor Green
        
        return $true
    } else {
        Write-Host "  ✗ Installation failed" -ForegroundColor Red
        return $false
    }
}

# Install to selected device(s)
$successCount = 0

if ($InstallToAllDevices) {
    foreach ($device in $deviceList) {
        if (Install-ToDevice $device.Id $device.Name) {
            $successCount++
        }
    }
} else {
    if (Install-ToDevice $selectedDevice.Id $selectedDevice.Name) {
        $successCount++
    }
}

# Show final results
Write-Host ""
Write-Host "Deployment Complete!" -ForegroundColor Green
Write-Host "====================" -ForegroundColor Green
Write-Host ""

if ($successCount -gt 0) {
    Write-Host "✓ Successfully installed to $successCount device(s)" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "1. Open '$AppName' app on your phone" -ForegroundColor White
    Write-Host "2. Tap 'Start Broadcasting' to begin advertising glucose data" -ForegroundColor White
    Write-Host "3. Use +10/-10 buttons to adjust glucose values for testing" -ForegroundColor White
    Write-Host "4. Run the Karoo deployment script to connect:" -ForegroundColor White
    Write-Host "   .\Deploy-KarooGlucometer.ps1 -SetupTestData `$true" -ForegroundColor Gray
    Write-Host ""
    Write-Host "The phone will now act as a BLE glucose monitor that your" -ForegroundColor Yellow
    Write-Host "Karoo can discover and connect to automatically!" -ForegroundColor Yellow
} else {
    Write-Host "✗ No devices were successfully configured" -ForegroundColor Red
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Yellow
    Write-Host "1. Ensure USB debugging is enabled" -ForegroundColor Gray
    Write-Host "2. Accept any permission prompts on your phone" -ForegroundColor Gray
    Write-Host "3. Try disconnecting and reconnecting USB cable" -ForegroundColor Gray
    Write-Host "4. Check if phone is in file transfer mode" -ForegroundColor Gray
}

Write-Host ""