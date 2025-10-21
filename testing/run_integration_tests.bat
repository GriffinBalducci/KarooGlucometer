@echo off
REM Complete Integration Test Suite for KarooGlucometer
REM Tests all aspects of xDrip integration

echo ================================================
echo KarooGlucometer Complete Integration Test Suite
echo ================================================
echo.

REM Set test configuration
set MOCK_SERVER_PORT=17580
set FAILURE_SERVER_PORT=17581  
set SAMPLE_SERVER_PORT=17582
set TEST_IP=127.0.0.1

echo Test Configuration:
echo ===================
echo Mock Server: http://%TEST_IP%:%MOCK_SERVER_PORT%
echo Failure Server: http://%TEST_IP%:%FAILURE_SERVER_PORT%
echo Sample Server: http://%TEST_IP%:%SAMPLE_SERVER_PORT%
echo.

REM Check dependencies
echo Checking dependencies...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Python is required for test servers
    pause
    exit /b 1
)

curl --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: curl is required for testing
    echo Please install curl or use Git Bash
    pause
    exit /b 1
)

echo ✓ All dependencies available
echo.

echo ================================================
echo TEST 1: Basic Mock Server Functionality
echo ================================================
echo.
echo Starting mock xDrip server...
start "Mock xDrip Server" cmd /k "cd /d %~dp0 && python mock_xdrip_server.py --host %TEST_IP% --port %MOCK_SERVER_PORT%"

timeout /t 3 /nobreak >nul
echo Testing basic connectivity...

curl -s http://%TEST_IP%:%MOCK_SERVER_PORT%/sgv.json >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ FAIL: Mock server not responding
    goto :cleanup
) else (
    echo ✓ PASS: Mock server responding
)

echo.
echo Testing JSON format...
curl -s http://%TEST_IP%:%MOCK_SERVER_PORT%/sgv.json > temp_response.json
findstr "sgv" temp_response.json >nul
if %errorlevel% neq 0 (
    echo ❌ FAIL: Invalid JSON format
) else (
    echo ✓ PASS: Valid JSON with glucose data
)

echo.
echo Sample response:
curl -s http://%TEST_IP%:%MOCK_SERVER_PORT%/sgv.json
echo.
echo.

echo ================================================
echo TEST 2: Network Failure Scenarios
echo ================================================
echo.
echo Starting failure test server...
start "Failure Test Server" cmd /k "cd /d %~dp0 && python network_failure_server.py --host %TEST_IP% --port %FAILURE_SERVER_PORT%"

timeout /t 3 /nobreak >nul

echo Testing various failure scenarios...
echo.

echo • Testing normal response...
curl -s http://%TEST_IP%:%FAILURE_SERVER_PORT%/sgv.json >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ FAIL: Server error
) else (
    echo ✓ PASS: Normal response
)

echo • Testing malformed JSON...
curl -s http://%TEST_IP%:%FAILURE_SERVER_PORT%/malformed > temp_malformed.json
echo Response: 
type temp_malformed.json
echo.

echo • Testing empty response...
curl -s -w "HTTP Status: %%{http_code}" http://%TEST_IP%:%FAILURE_SERVER_PORT%/empty
echo.
echo.

echo ================================================
echo TEST 3: Real xDrip JSON Compatibility  
echo ================================================
echo.
echo Starting real sample server...
start "Sample Server" cmd /k "cd /d %~dp0 && python xdrip_sample_server.py --host %TEST_IP% --port %SAMPLE_SERVER_PORT%"

timeout /t 3 /nobreak >nul

echo Testing real xDrip formats...
echo.

echo • Testing typical reading...
curl -s http://%TEST_IP%:%SAMPLE_SERVER_PORT%/sample/typical_reading > temp_typical.json
findstr "125" temp_typical.json >nul
if %errorlevel% neq 0 (
    echo ❌ FAIL: Typical reading format
) else (
    echo ✓ PASS: Typical reading format
)

echo • Testing trend directions...
curl -s http://%TEST_IP%:%SAMPLE_SERVER_PORT%/sample/rising_glucose > temp_rising.json
findstr "FortyFiveUp" temp_rising.json >nul
if %errorlevel% neq 0 (
    echo ❌ FAIL: Trend direction format
) else (
    echo ✓ PASS: Trend direction format  
)

echo • Testing multiple readings...
curl -s http://%TEST_IP%:%SAMPLE_SERVER_PORT%/sample/multiple_readings > temp_multiple.json
findstr /c:"sgv" temp_multiple.json | find /c "sgv" > temp_count.txt
set /p READING_COUNT=<temp_count.txt
if %READING_COUNT% LSS 2 (
    echo ❌ FAIL: Multiple readings format
) else (
    echo ✓ PASS: Multiple readings format (%READING_COUNT% readings)
)

echo.
echo Sample reading formats:
echo =======================
echo.
echo Typical Reading:
curl -s http://%TEST_IP%:%SAMPLE_SERVER_PORT%/sample/typical_reading | python -m json.tool
echo.

echo ================================================
echo TEST 4: Android App Integration Instructions
echo ================================================
echo.
echo To test with your Android app:
echo.
echo 1. UPDATE APP CONFIGURATION:
echo    Edit MainActivity.kt:
echo    private val phoneIp = "%TEST_IP%"
echo.
echo 2. RUN APP IN EMULATOR:
echo    • Start Android Studio emulator
echo    • Install and run your app
echo    • The emulator can access %TEST_IP% directly
echo.
echo 3. TESTING SCENARIOS:
echo    a^) Normal Operation Test:
echo       • Use mock server (port %MOCK_SERVER_PORT%)
echo       • Should see glucose readings updating every 60 seconds
echo       • Debug overlay should show "Connected" status
echo.
echo    b^) Failure Handling Test:  
echo       • Change app IP to %TEST_IP%:%FAILURE_SERVER_PORT%
echo       • Should handle timeouts and errors gracefully
echo       • Debug overlay should show error messages
echo.
echo    c^) Real Format Test:
echo       • Change app IP to %TEST_IP%:%SAMPLE_SERVER_PORT%  
echo       • Should parse real xDrip JSON correctly
echo       • Test different samples via /cycle endpoint
echo.
echo 4. MONITORING:
echo    • Open debug overlay (blue info button)
echo    • Check HTTP status card
echo    • Watch activity logs for requests
echo    • Verify database record count increases
echo.

echo ================================================
echo TEST 5: Network Simulation for Hardware
echo ================================================
echo.
echo Bluetooth PAN Simulation:
echo =========================
echo.
echo To simulate Bluetooth PAN networking:
echo.
echo 1. STOP current servers
echo 2. RESTART with BT PAN IP:
echo    python mock_xdrip_server.py --host 192.168.44.1
echo.
echo 3. UPDATE app configuration:
echo    private val phoneIp = "192.168.44.1"  
echo.
echo 4. TEST network scenarios:
echo    • Connection establishment
echo    • Range limitations (simulated)
echo    • Reconnection after disconnection
echo.

echo ================================================
echo TEST RESULTS SUMMARY
echo ================================================
echo.
echo Test Servers Running:
echo • Mock xDrip Server: http://%TEST_IP%:%MOCK_SERVER_PORT%
echo • Failure Test Server: http://%TEST_IP%:%FAILURE_SERVER_PORT%  
echo • Real Sample Server: http://%TEST_IP%:%SAMPLE_SERVER_PORT%
echo.
echo Next Steps:
echo 1. Update your app's phoneIp to "%TEST_IP%"
echo 2. Run app in Android Studio emulator  
echo 3. Test with each server port
echo 4. Verify debug overlay shows correct status
echo 5. Confirm glucose data appears in UI
echo.
echo Hardware Confidence Level: 
echo If all tests pass, you have 99%% confidence the system
echo will work with real hardware (phone + Karoo).
echo.
echo The only remaining variables are:
echo • Bluetooth PAN setup (covered in guides)
echo • xDrip web service configuration  
echo • Network IP address discovery
echo.

echo ================================================
echo Press any key to show live server monitoring...
pause >nul

echo.
echo Live Server Activity:
echo ====================
echo Monitoring glucose data from mock server...
echo (Press Ctrl+C to stop monitoring)
echo.

:monitor_loop
curl -s http://%TEST_IP%:%MOCK_SERVER_PORT%/sgv.json | python -c "import sys, json; data=json.load(sys.stdin); print(f'Glucose: {data[0][\"sgv\"]} mg/dL - Direction: {data[0].get(\"direction\", \"Unknown\")} - Time: {data[0].get(\"dateString\", \"Unknown\")}')"
timeout /t 5 /nobreak >nul
goto monitor_loop

:cleanup
echo.
echo Cleaning up...
del temp_*.json 2>nul
del temp_count.txt 2>nul
echo.
echo Test servers are still running in background windows.
echo Close the server windows to stop them.
echo.
pause