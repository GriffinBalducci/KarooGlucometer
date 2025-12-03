@echo off
setlocal enabledelayedexpansion

echo.
echo ===============================================
echo  BLE Glucose Broadcaster - Easy Install
echo ===============================================
echo.
echo This will install the glucose broadcaster app to your phone.
echo Your phone will simulate a glucose monitor for Karoo testing.
echo.

:: Check for ADB
where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: ADB not found!
    echo.
    echo Please install Android SDK Platform Tools:
    echo https://developer.android.com/studio/releases/platform-tools
    echo.
    echo Or install Android Studio which includes ADB.
    echo.
    pause
    exit /b 1
)

:: Check for devices
echo Checking for connected Android devices...
for /f "tokens=1" %%i in ('adb devices ^| findstr device$ ^| findstr /v "List of devices"') do (
    set devicefound=%%i
    goto :devicecheck
)

:nodevice
echo.
echo ERROR: No Android device found!
echo.
echo Please follow these steps:
echo 1. Connect your Android phone via USB
echo 2. Enable Developer Options on your phone:
echo    - Go to Settings ^> About Phone
echo    - Tap 'Build Number' 7 times
echo 3. Enable 'USB Debugging' in Developer Options
echo 4. Accept the USB debugging prompt on your phone
echo 5. Run this script again
echo.
pause
exit /b 1

:devicecheck
if not defined devicefound goto :nodevice

echo Found device: %devicefound%
echo.

:: Build the app
echo Building the BLE Broadcaster app...
cd BLE_Broadcaster
if not exist gradlew.bat (
    echo Gradle wrapper not found. Please run the PowerShell script instead.
    echo .\Deploy-BLE-Broadcaster.ps1
    pause
    exit /b 1
)

call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo.
    echo Build failed! Please install Android Studio or run:
    echo .\Deploy-BLE-Broadcaster.ps1
    pause
    exit /b 1
)

cd ..

:: Check for APK
if not exist "BLE_Broadcaster\app\build\outputs\apk\debug\app-debug.apk" (
    echo APK file not found after build!
    pause
    exit /b 1
)

echo.
echo Installing app to your phone...

:: Install APK
adb -s %devicefound% install -r "BLE_Broadcaster\app\build\outputs\apk\debug\app-debug.apk"
if %errorlevel% neq 0 (
    echo Installation failed!
    pause
    exit /b 1
)

echo.
echo Granting permissions...

:: Grant permissions
adb -s %devicefound% shell pm grant com.example.bleglucosebroadcaster android.permission.BLUETOOTH >nul
adb -s %devicefound% shell pm grant com.example.bleglucosebroadcaster android.permission.BLUETOOTH_ADMIN >nul
adb -s %devicefound% shell pm grant com.example.bleglucosebroadcaster android.permission.ACCESS_FINE_LOCATION >nul
adb -s %devicefound% shell pm grant com.example.bleglucosebroadcaster android.permission.ACCESS_COARSE_LOCATION >nul
adb -s %devicefound% shell pm grant com.example.bleglucosebroadcaster android.permission.BLUETOOTH_ADVERTISE >nul 2>nul
adb -s %devicefound% shell pm grant com.example.bleglucosebroadcaster android.permission.BLUETOOTH_CONNECT >nul 2>nul
adb -s %devicefound% shell pm grant com.example.bleglucosebroadcaster android.permission.BLUETOOTH_SCAN >nul 2>nul

:: Launch app
echo Launching app...
adb -s %devicefound% shell am start -n com.example.bleglucosebroadcaster/.MainActivity >nul

echo.
echo ==============================
echo   INSTALLATION COMPLETE!
echo ==============================
echo.
echo The BLE Glucose Broadcaster app is now installed on your phone.
echo.
echo NEXT STEPS:
echo 1. Open 'BLE Glucose Broadcaster' app on your phone
echo 2. Tap 'Start Broadcasting' to begin glucose simulation
echo 3. Use +10/-10 buttons to adjust test glucose values
echo 4. Run the Karoo script: Deploy-KarooGlucometer.ps1
echo.
echo Your phone will now act as a fake glucose monitor!
echo.
pause