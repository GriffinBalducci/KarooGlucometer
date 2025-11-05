@echo off
REM Comprehensive Testing Script for KarooGlucometer
REM Tests the complete xDrip integration without hardware

echo ============================================
echo KarooGlucometer Hardware-Free Testing Suite
echo ============================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.7+ to run the mock server
    pause
    exit /b 1
)

echo Step 1: Starting Mock xDrip Server...
echo =====================================
echo.
echo This server mimics xDrip+ HTTP service at port 17580
echo It will serve realistic glucose data for testing
echo.

REM Start the mock server in background
start "Mock xDrip Server" cmd /k "cd /d %~dp0 && python mock_xdrip_server.py --host 127.0.0.1 --port 17580"

REM Wait for server to start
echo Waiting for server to initialize...
timeout /t 3 /nobreak >nul

echo.
echo Step 2: Testing Server Connectivity...
echo ======================================
echo.

REM Test if server is responding
curl -s http://127.0.0.1:17580/sgv.json >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Mock server is not responding
    echo Please check if port 17580 is available
    pause
    exit /b 1
) else (
    echo ✓ Mock server is running and responding
)

echo.
echo Step 3: Server Endpoints Available...
echo ====================================
echo.
echo • Glucose Data: http://127.0.0.1:17580/sgv.json
echo • Server Status: http://127.0.0.1:17580/status  
echo • Web Interface: http://127.0.0.1:17580/
echo.

echo Step 4: Testing Glucose Data Format...
echo ======================================
echo.
echo Sample glucose response:
curl -s http://127.0.0.1:17580/sgv.json
echo.
echo.

echo Step 5: Update Your App Configuration...
echo ========================================
echo.
echo To test with your Android app:
echo 1. Update MainActivity.kt phoneIp to: "127.0.0.1"
echo 2. Run your app in Android Studio emulator
echo 3. The emulator can access localhost via 127.0.0.1
echo 4. Watch the debug overlay for connection status
echo.

echo Step 6: Network Simulation Options...
echo =====================================
echo.
echo For Bluetooth PAN simulation:
echo • Use host: 192.168.44.1 (typical BT PAN host IP)
echo • Test with: python mock_xdrip_server.py --host 192.168.44.1
echo • Update app IP to: "192.168.44.1"
echo.

echo Step 7: Automated Testing Available...
echo ======================================
echo.
echo Run the following for comprehensive testing:
echo • test_integration.bat - Full app integration test
echo • test_network_failures.bat - Connection failure scenarios
echo • test_json_compatibility.bat - Real xDrip data samples
echo.

echo ============================================
echo Testing Environment Ready!
echo ============================================
echo.
echo The mock xDrip server is now running.
echo Open your Android Studio emulator and test your app.
echo.
echo Press any key to show real-time server logs...
pause >nul

REM Show server logs
echo.
echo Real-time server activity (Ctrl+C to stop):
echo ===========================================
curl -s http://127.0.0.1:17580/sgv.json | python -m json.tool

echo.
echo Server is running in background window.
echo Close the "Mock xDrip Server" window to stop it.
echo.
pause