# KarooGlucometer Testing Guide

## Overview
This guide helps you test the three main features we implemented:
1. **IP Configuration** - Runtime IP changes through debug overlay
2. **Chart Performance** - Optimized chart rendering without connection issues
3. **Karoo Metrics Integration** - Glucose data publishing to Karoo ride metrics

## Prerequisites
- Android Studio or VS Code with Android development setup
- Android device or emulator
- Python 3.x for mock server testing

## Testing Steps

### 1. Install and Launch the App

```bash
# Build and install the debug APK
./gradlew installDebug

# Or just build if installing manually
./gradlew assembleDebug
```

The APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Test IP Configuration Feature

**Purpose**: Verify the debug overlay allows runtime IP configuration

**Steps**:
1. Launch the app
2. **Open Debug Overlay**: Tap anywhere on the main screen to open the debug overlay
3. **Locate IP Configuration Section**: Look for the "IP Configuration" card at the top
4. **Current IP Display**: Should show current phone IP (default: `10.0.2.2` for emulator)
5. **Change IP**: 
   - Enter a new IP in the text field (e.g., `192.168.1.100`)
   - Tap "Apply" button
   - Verify the "Current Phone IP" updates immediately
6. **Test Connection**: The app should attempt connections to the new IP
7. **Close Overlay**: Tap the X button to close debug overlay

**Expected Results**:
- [PASS] Debug overlay opens when tapping the screen
- [PASS] IP input field is visible and functional
- [PASS] Apply button updates the IP immediately
- [PASS] No app restart required for IP changes
- [PASS] Connection attempts use the new IP address

### 3. Test Chart Performance Impact

**Purpose**: Verify chart rendering doesn't interfere with data fetching

**Setup Mock Server**:
```bash
# In project root directory
```bash
python testing/mock_server.py
```

**Steps**:
1. Start the mock server (provides test glucose data)
2. Open debug overlay and set IP to `127.0.0.1` (or `10.0.2.2` for emulator)
3. Close debug overlay
4. **Monitor Performance**:
   - Watch the glucose chart update smoothly
   - Verify background data fetching continues (check debug overlay logs)
   - Ensure no connection timeouts or failures
5. **Chart Interactions**:
   - Try zooming/panning the chart
   - Verify chart animations are disabled (should be snappy, not slow)
   - Check that chart interactions don't interrupt data fetching

**Expected Results**:
- [PASS] Chart renders smoothly without lag
- [PASS] Background data fetching continues uninterrupted
- [PASS] No connection timeouts due to chart processing
- [PASS] Chart animations are disabled for performance
- [PASS] Chart interactions are responsive

### 4. Test Karoo Metrics Integration

**Purpose**: Verify glucose data is broadcast to Karoo system

**Steps**:
1. Ensure app is receiving glucose data (use mock server if needed)
2. **Check Logs**: Open debug overlay and look for log messages like:
   - "Publishing to Karoo: [glucose_value] mg/dL"
   - "Published glucose metric: [value] mg/dL to Karoo"
3. **Test Alert Levels**:
   - Use mock server or modify test values
   - Test critical low (<70 mg/dL) - should see "CRITICAL LOW" alerts
   - Test warning high (>180 mg/dL) - should see "HIGH GLUCOSE" alerts
   - Test normal range (70-180 mg/dL) - should see normal metrics
4. **Test Trend Data**:
   - Verify trend calculations with multiple readings
   - Check for trend symbols (↗, ↘, →) in logs

**Expected Results**:
- [PASS] Karoo service registers on app startup
- [PASS] Glucose readings are published to Karoo via broadcast intents
- [PASS] Alert levels are correctly calculated and published
- [PASS] Trend data is calculated and published when multiple readings exist
- [PASS] All Karoo broadcasts include proper permissions and data structure

### 5. Test Real Connection Mode

**Purpose**: Verify app works with actual xDrip data source

**Setup**:
```bash
# Start mock xDrip server to simulate real xDrip
python testing/mock_xdrip_server.py
```

**Steps**:
1. Verify `useTestData = false` in MainActivity.kt (should be false by default now)
2. Start mock server
3. Configure IP in debug overlay to point to mock server
4. **Monitor Connection**:
   - Check debug overlay for HTTP status
   - Verify connection test passes before HTTP requests
   - Watch for successful glucose data retrieval
   - Monitor database updates in debug overlay

**Expected Results**:
- [PASS] App attempts real HTTP connections (not using test data)
- [PASS] Connection pre-testing works correctly
- [PASS] Successful HTTP requests fetch JSON glucose data
- [PASS] Database updates with real glucose readings
- [PASS] Karoo metrics publish real data (not test data)

## Debug Information

### Key Log Messages to Look For:
- `"Starting glucose fetch from [IP]"` - Connection attempt started
- `"Connection test passed, fetching glucose data..."` - Pre-connection test succeeded
- `"Glucose fetch successful"` - Data retrieved successfully
- `"Publishing to Karoo: [value] mg/dL"` - Karoo metrics publishing
- `"IP changed to [new_ip]"` - IP configuration changed

### Common Issues and Solutions:

**Connection Failures**:
- Check if mock server is running
- Verify IP address is correct in debug overlay
- Check firewall/network permissions

**Chart Performance Issues**:
- Verify `enableChartAnimations = false`
- Check if background data fetching continues in debug logs

**Karoo Integration Issues**:
- Check for Karoo service initialization logs
- Verify broadcast intent permissions
- Look for glucose metric publishing logs

## Advanced Testing

### Test with Real Device and xDrip:
1. Install on Android device
2. Set up Bluetooth PAN connection to phone running xDrip
3. Configure real phone IP address in debug overlay
4. Test end-to-end glucose data flow from xDrip → Karoo app → Karoo metrics

### Performance Testing:
1. Monitor memory usage during chart updates
2. Test with rapid glucose reading updates
3. Verify app stability over extended periods
4. Test chart performance with large datasets

## Troubleshooting

### Build Issues:
```bash
# Clean and rebuild if needed
./gradlew clean
./gradlew assembleDebug
```

### Server Issues:
```bash
# If Python server won't start
pip install --upgrade pip
python -m http.server 17580  # Simple fallback server
```

### Network Issues:
- Emulator: Use `10.0.2.2` to access host machine
- Real device: Use actual computer IP (e.g., `192.168.1.100`)
- Check firewall allows port 17580

## Success Criteria

[PASS] **IP Configuration**: Debug overlay allows runtime IP changes without restart
[PASS] **Chart Performance**: Chart rendering doesn't interfere with data fetching
[PASS] **Karoo Integration**: Glucose data is properly broadcast to Karoo metrics system
[PASS] **Real Connections**: App successfully fetches data from actual HTTP sources
[PASS] **Stability**: App runs reliably without crashes or connection issues