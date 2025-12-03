# KarooGlucometer

Comprehensive glucose monitoring solution for Hammerhead Karoo cycling computers with intelligent dual-source data management.

## Overview

KarooGlucometer provides real-time glucose monitoring for cyclists using Hammerhead Karoo devices. The system intelligently manages multiple data sources (onboard xDrip+ and external BLE glucose monitors) with automatic failover, comprehensive health monitoring, and data validation.

## Core Features

- **Dual Data Sources**: Automatic switching between onboard xDrip+ and external BLE glucose monitors
- **Connection Health Monitoring**: Real-time stability analysis with automatic recovery
- **Data Validation**: Multi-tier quality checking with outlier detection and consistency monitoring
- **Comprehensive Debug Interface**: Real-time connection status and troubleshooting information
- **Automated Deployment**: PowerShell script for complete setup including test data injection

## Quick Start

### Requirements
- Hammerhead Karoo device
- USB debugging enabled
- **ADB automatically configured by scripts** (no manual installation needed)
- Optional: xDrip+ installed for onboard glucose monitoring
- Optional: BLE glucose monitor for external monitoring



# **Android BLE Broadcaster – Quick Setup**

### With Phone, Scan this QR Code and follow setup prompts:

<img width="500" height="500" alt="broadcasterqr" src="https://github.com/user-attachments/assets/a552c805-c6f2-4e06-8b42-a091db46ec86" />


# **Karoo APK Installation – Quick Setup**

### **1. Download Requirements**

- **Android Platform Tools**  
    [https://developer.android.com/tools/releases/platform-tools](https://developer.android.com/tools/releases/platform-tools)  
    → Download and unzip.
    
- **Receiver APK**  
    [https://github.com/GriffinBalducci/KarooGlucometer/releases/download/1.1/receiver.apk](https://github.com/GriffinBalducci/KarooGlucometer/releases/download/1.1/receiver.apk)  
    → Save/transfer this file into the unzipped **platform-tools** folder.
    

---

### **2. Connect & Verify Device**

After connecting to Karoo via USB, in your terminal, navigate to the **platform-tools** directory:

`cd path/to/platform-tools`

Check that the Karoo is detected:

`./adb devices`

Copy the device name from the list (e.g., `KAROO20ALC030802155`).

---

### **3. Install the APK**

`./adb -s <DEVICE_NAME> install receiver.apk`

Replace `<DEVICE_NAME>` with the name shown by `adb devices`.




## Architecture

### Data Sources

**Primary: Onboard xDrip+ Content Provider**
- Accesses local xDrip+ database via content provider
- Higher reliability when xDrip+ is properly configured
- Automatic fallback if data becomes stale (> 5 minutes)

**Secondary: External BLE GATT**
- Scans for standard Bluetooth glucose monitors
- Uses Bluetooth Glucose Service (UUID 1808)
- Automatic connection and service discovery
- Real-time glucose measurements via notifications

**Testing: BLE_Broadcaster Phone App**
- Complete Android app that simulates a glucose monitor
- Advertises standard Bluetooth SIG Glucose Service UUID
- GATT server with proper glucose measurement characteristics
- Real-time glucose value adjustment for testing scenarios
- Automatic deployment via `Install-Phone-App.bat`

### Intelligent Source Switching

The system continuously monitors both sources and automatically switches based on:
- Data freshness (< 5 minutes preferred)
- Connection stability and signal quality
- Data validation scores and consistency
- Source availability and health status

### Health Monitoring System

**ConnectionHealthMonitor**
- Overall health scoring (Excellent to Critical)
- Connection uptime and stability tracking
- Automatic reconnection attempts and recovery
- Error rate monitoring and corrective actions

**GlucoseDataValidator**
- Range validation (40-600 mg/dL)
- Rate-of-change analysis (< 10 mg/dL/min)
- Statistical outlier detection using Z-scores
- Source consistency monitoring between BLE and xDrip+

### Debug Interface

Real-time monitoring dashboard provides:
- BLE connection details (device, signal, services)
- xDrip+ status and data freshness
- Data quality metrics and validation scores
- Connection stability analysis and health scores
- Source switching logic and failover status

## Project Structure

```
KarooGlucometer/
├── app/                          # Main Android application
│   ├── src/main/java/com/example/karooglucometer/
│   │   ├── MainActivity.kt       # Main application entry point
│   │   ├── adapter/             # BLE database integration
│   │   ├── ble/                # Bluetooth GATT implementation
│   │   ├── datasource/         # Intelligent source management
│   │   ├── monitoring/         # Health monitoring and debug UI
│   │   ├── validation/         # Data quality validation
│   │   └── xdrip/              # xDrip+ content provider access
│   └── build.gradle.kts        # App build configuration
├── connexx_backup/             # Phone connection app backup
├── BLE_Broadcaster/           # BLE testing and broadcasting tools
├── testing/                   # Testing infrastructure
│   ├── mock_server.py         # Multi-mode glucose data server
│   ├── connection_tester.py   # Comprehensive connection diagnostics
│   └── dependency_analyzer.py # Build and dependency analysis
└── Deploy-KarooGlucometer.ps1 # Automated deployment script
```

## Testing

### Mock Server Testing
```bash
# Normal glucose data simulation
python testing/mock_server.py --mode normal

# Error condition testing
python testing/mock_server.py --mode error

# Timeout scenario testing  
python testing/mock_server.py --mode slow
```

### Connection Diagnostics
```bash
# Comprehensive connection analysis
python testing/connection_tester.py

# Quick test specific IP address
python testing/connection_tester.py --quick 192.168.44.1
```

### Build Validation
```bash
# Check for dependency issues
python testing/dependency_analyzer.py

# Clean build verification
./gradlew clean assembleDebug
```

## xDrip+ Phone Setup

For onboard xDrip+ functionality, configure the phone with:

**Web Service Configuration**
- Settings → Inter-app Settings → Web Service API
- Enable Web Service: ON
- Port: 17580
- Allow LAN connections: ON

**Power Management**
- Disable battery optimization for xDrip+
- Allow background activity
- Set to unrestricted power usage

**Network Connectivity**
- Bluetooth PAN (recommended for cycling)
- Same WiFi network (alternative)
- Mobile hotspot (fallback option)

## Troubleshooting

### xDrip+ Configuration Issues

**No glucose data appearing:**
1. Verify Engineering Mode is enabled: Menu → Engineering Mode
2. Check data source: Settings → Data Source → should be "Follower"
3. Enable inter-app communication: Settings → Inter-app Settings → Accept Followers = ON
4. Manually inject test data: Engineering Mode → Add Glucose Data

**Web service not responding:**
1. Restart xDrip+ web service: Settings → Cloud Upload → REST API → toggle off/on
2. Check port 17580: `adb shell netstat | grep 17580`
3. Verify web service enabled: Settings → Less Common Settings → External Status Service = ON
4. Test manually: `curl http://localhost:17580/sgv.json`

**Engineering Mode not available:**
1. Enable via broadcast: `adb shell am broadcast -a com.eveningoutpost.dexdrip.ENABLE_ENGINEERING_MODE`
2. Alternative: Settings → Less Common Settings → Engineering Mode = ON
3. Restart xDrip+ after enabling
4. Check Menu for "Engineering Mode" option

**Permission issues:**
1. Re-run setup script: `.\Setup-XDrip-Advanced.ps1`
2. Manually grant permissions via Settings → Apps → xDrip+ → Permissions
3. Disable battery optimization for xDrip+
4. Allow background activity in Android settings

### Connection Issues
1. Check debug overlay connection status
2. Verify xDrip+ web service configuration (port 17580)
3. Test network connectivity between devices
4. Check Bluetooth permissions and adapter status

### Data Quality Problems
1. Review validation metrics in debug overlay
2. Check for statistical outliers or rate-of-change issues
3. Verify source consistency between BLE and xDrip+
4. Monitor connection stability scores

### BLE Scanning Problems
1. Ensure all Bluetooth permissions granted
2. Check device location services enabled
3. Verify glucose monitor is in pairing mode
4. Monitor BLE GATT connection state in debug overlay

## Technology Stack

- **Language**: Kotlin with coroutines for async operations
- **UI Framework**: Jetpack Compose with Material 3
- **Database**: Room for local glucose data storage
- **Bluetooth**: BLE GATT for standard glucose service access
- **Content Provider**: xDrip+ database access via Android content providers
- **Monitoring**: Custom health monitoring with StateFlow reactive streams
- **Build System**: Gradle with Kotlin DSL and version catalogs

## Development

### Building
```bash
# Debug build
./gradlew assembleDebug

# Release build  
./gradlew assembleRelease
```

### Testing Integration
```bash
# Run all tests
./gradlew test

# Integration test suite
testing/run_integration_tests.bat
```

### Code Quality
```bash
# Dependency analysis
python testing/dependency_analyzer.py

# Build verification
./gradlew check
```

## Contributing

1. Ensure comprehensive testing with both BLE and xDrip+ sources
2. Update health monitoring metrics for new features
3. Test data validation with edge cases and outliers
4. Verify deployment script compatibility with changes
5. Document connection requirements and setup procedures

## License

This project is developed for Hammerhead Karoo glucose monitoring integration.
