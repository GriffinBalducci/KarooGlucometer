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

### Installation

**For complete beginners - use the automated scripts (ADB auto-installed):**

1. **Setup Android Phone (BLE Broadcaster)**:
   ```batch
   Install-Phone-App.bat
   ```
   OR for advanced users:
   ```powershell
   .\Deploy-BLE-Broadcaster.ps1
   ```

2. **Setup Karoo Device**:
   ```powershell
   .\Deploy-KarooGlucometer.ps1 -SetupTestData $true
   ```

**What the scripts do automatically:**
- **Phone script**: Builds and installs BLE Glucose Broadcaster app, grants all permissions, launches app
- **Karoo script**: Builds main app, configures xDrip+, grants BLE permissions, starts monitoring

### Manual Installation (Advanced)

If you prefer manual control:

1. Connect Karoo device via USB
2. Connect Android phone via USB (for BLE broadcaster)
3. Run Karoo deployment script:
```powershell
.\Deploy-KarooGlucometer.ps1 -SetupTestData $true
```

The script automatically:
- Detects connected Karoo device
- Builds and installs the application
- Grants required BLE permissions
- **Comprehensive xDrip+ configuration** (Engineering Mode, Web Service, Test Data)
- Starts the application

### Advanced xDrip+ Setup

For complex xDrip+ scenarios, use the dedicated setup script:
```powershell
.\Setup-XDrip-Advanced.ps1 -XDripApkPath "path\to\xdrip.apk"
```

This comprehensive script:
- Installs/reinstalls xDrip+ if needed
- Enables Engineering Mode for test data injection
- Configures Follower mode for external data sources
- Sets up web service on port 17580 with CORS support
- Injects 2 hours of realistic test glucose data
- Verifies all services are running correctly

### xDrip+ Configuration Details

The deployment automatically handles the complex xDrip+ setup:
- **Engineering Mode**: Enabled for test data injection and advanced features
- **Data Source**: Configured to "Follower" mode for external glucose monitors
- **Web Service**: Port 17580 enabled with authentication disabled for local access
- **Permissions**: All Bluetooth, location, and network permissions granted
- **Test Data**: Realistic glucose readings with trends and directions
- **Service Management**: Automatic startup of all required background services

### Usage

**Complete Setup for Beginners:**

1. **Setup Phone**: Run `Install-Phone-App.bat` (connects your phone as fake glucose monitor)
2. **Setup Karoo**: Run `.\Deploy-KarooGlucometer.ps1 -SetupTestData $true`
3. **Start Broadcasting**: Open BLE Broadcaster app on phone → tap "Start Broadcasting"
4. **Open Karoo App**: KarooGlucometer will automatically detect and connect to phone
5. **Monitor Data**: Tap Info button on Karoo to access debug overlay and connection status

**Testing and Debugging:**

1. **Phone App**: Use +/- buttons to adjust glucose values in real-time
2. **Karoo Debug Overlay**: Monitor BLE GATT and xDrip+ connection health
3. **Data Validation**: Check data quality metrics and source consistency
4. **Connection Status**: Real-time monitoring of both data sources

**Advanced Configuration:**

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