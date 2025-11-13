# xDrip+ and Android Phone Setup Guide

This document outlines all the required settings and configurations needed on the Android phone running xDrip+ to properly work with the Karoo Glucometer app.

## Phone-Side Requirements

### 1. xDrip+ Installation and Basic Setup

#### Initial Installation
- Install xDrip+ from GitHub releases or F-Droid
- Grant all requested permissions during setup
- Complete initial sensor setup wizard

#### Core Sensor Configuration
- **Settings → Hardware Data Source**
  - Select your CGM type (Dexcom G6/G7, Libre, etc.)
  - Configure sensor-specific settings
  - Enable "Start Sensor" if applicable

### 2. Web Service Configuration (Critical)

#### Enable Web Service
- **Settings → Inter-app Settings → Web Service API**
  - ✅ **Enable Web Service** - MUST be enabled
  - **Port**: `17580` (default, can be changed if needed)
  - ✅ **Allow LAN connections** - MUST be enabled for Karoo access

#### API Endpoints Configuration
- **Settings → Inter-app Settings → Web Service API → Web Service Settings**
  - ✅ **Enable sgv.json endpoint** - Required for glucose readings
  - ✅ **Enable entries.json endpoint** - Required for historical data
  - ✅ **Enable status.json endpoint** - Required for sensor status
  - ✅ **Enable treatments.json endpoint** - Optional but recommended

### 3. Network and Connectivity

#### Bluetooth PAN (Recommended)
- Pair phone and Karoo via Bluetooth
- Enable Bluetooth tethering/PAN on phone
- Connect Karoo to phone's Bluetooth network
- More reliable for cycling - no WiFi dependency

#### WiFi Network (Alternative)
- Ensure phone and Karoo are on the **same WiFi network** if using WiFi
- Check for network isolation settings on router (disable if present)
- Verify phone has stable WiFi connection

#### Mobile Hotspot (Alternative)
- Enable mobile hotspot on phone
- Connect Karoo to phone's hotspot network
- Note: May consume mobile data

#### IP Address Discovery
- **Settings → System Status** in xDrip+ shows current IP address
- Alternative: Check in phone's network settings (WiFi or Bluetooth PAN)
- Example format: `192.168.1.100` (WiFi) or `192.168.44.1` (Bluetooth PAN)

### 4. Android System Settings

#### Power Management
- **Settings → Apps → xDrip+ → Battery**
  - ✅ **Allow background activity**
  - ❌ **Optimize battery usage** (turn OFF)
  - Set to "Unrestricted" or "Don't optimize"

#### Network Permissions
- **Settings → Apps → xDrip+ → Permissions**
  - ✅ **Location** (required for some CGM connections)
  - ✅ **Phone** (if using phone alerts)
  - ✅ **Storage** (for data logging)

#### Do Not Disturb / Focus Modes
- Ensure xDrip+ is exempt from any battery saving modes
- Add to "Important apps" list if available

### 5. xDrip+ Data and Alerts

#### Glucose Data Quality
- **Settings → Glucose Settings**
  - Verify calibration is up to date
  - Check sensor connectivity status
  - Ensure readings are coming in regularly

#### Noise Filtering (Optional but Recommended)
- **Settings → Glucose Settings → Smooth sensor noise**
  - ✅ Enable for cleaner data transmission to Karoo

### 6. Firewall and Security

#### Windows Firewall (if applicable)
- Usually not an issue on Android, but check if using Android-x86

#### Router/Network Firewall
- Ensure port `17580` is not blocked on local network
- Check for device isolation settings

## Testing the Setup

### 1. Verify xDrip+ Web Service
1. Open web browser on phone
2. Navigate to: `http://localhost:17580/api/v1/status.json`
3. Should return JSON data about xDrip+ status

### 2. Test from Another Device (Karoo)
1. Get phone's IP address from xDrip+ System Status
2. On Karoo, test: `http://[PHONE_IP]:17580/api/v1/status.json`
3. Should return the same JSON data

### 3. Glucose Reading Test
1. Navigate to: `http://[PHONE_IP]:17580/api/v1/sgv.json?count=1`
2. Should return recent glucose reading in JSON format

## Common Troubleshooting

### Web Service Not Responding
- Verify "Enable Web Service" is checked
- Verify "Allow LAN connections" is checked
- Restart xDrip+ app
- Restart phone WiFi connection

### Karoo Can't Connect
- Verify connection method (Bluetooth PAN or same WiFi network)
- Double-check IP address (changes when switching connection types)
- Check router firewall/isolation settings (WiFi only)
- Ensure phone isn't in power saving mode

### Glucose Readings Not Updating
- Check CGM sensor connection in xDrip+
- Verify recent calibration
- Check Hardware Data Source configuration
- Look for error messages in System Status

## Required Information for Karoo Setup

When configuring the Karoo Glucometer app, you'll need:

1. **Phone IP Address**: Found in xDrip+ → Settings → System Status
   - Example: `192.168.1.100`
2. **Port Number**: Default `17580` (unless changed)

---

**Note**: This configuration enables the Karoo device to read glucose data from xDrip+ over Bluetooth PAN or WiFi connection. Bluetooth PAN is recommended for cycling as it doesn't require external WiFi networks and provides a more reliable connection during rides.