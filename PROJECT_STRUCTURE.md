# KarooGlucometer Project Structure

## Overview
Clean, efficient project structure with consolidated testing tools and modern Android development setup.

## Directory Structure

```
KarooGlucometer/
├── app/                          # Main Android application
│   ├── src/main/java/           # Kotlin source code
│   │   └── com/example/karooglucometer/
│   │       ├── MainActivity.kt   # Main app with Bluetooth PAN detection
│   │       ├── SimpleDebugOverlay.kt  # Professional debug interface
│   │       ├── NetworkDetector.kt     # Bluetooth PAN network detection
│   │       ├── KarooMetricsService.kt # Karoo integration service
│   │       └── data/            # Room database entities and DAOs
│   ├── src/main/res/           # Android resources
│   └── build.gradle.kts        # App-level build configuration
├── gradle/                     # Gradle configuration
│   └── libs.versions.toml     # Version catalog (modern dependency management)
├── testing/                   # Consolidated testing infrastructure
│   ├── mock_server.py         # Multi-mode mock server (normal/error/slow/empty)
│   ├── connection_tester.py   # Comprehensive connection diagnostics
│   ├── dependency_analyzer.py # Build and dependency issue analysis
│   ├── xdrip_json_samples.json # Real xDrip data samples
│   ├── run_integration_tests.bat # Automated test runner
│   ├── run_tests.bat          # Basic test runner
│   └── README.md              # Testing guide
├── build.gradle.kts           # Project-level build configuration
├── TESTING_GUIDE.md           # Main testing documentation
├── PROJECT_STRUCTURE.md       # This file
└── README.md                  # Project overview
```

## Key Components

### 🚀 Main Application (`app/`)
- **Modern Android Architecture**: Jetpack Compose + Material 3
- **Bluetooth PAN Detection**: Real-time network type detection
- **Professional Debug Interface**: Clean debug overlay with network status
- **Room Database**: Local glucose data storage with proper schema management
- **Karoo Integration**: Broadcast glucose metrics to Karoo devices

### 🧪 Testing Infrastructure (`testing/`)
**Consolidated from 14+ redundant files to 3 core tools:**

1. **`mock_server.py`** - Unified mock server
   - Normal mode: Realistic glucose data
   - Error mode: HTTP error simulation
   - Slow mode: Timeout testing
   - Empty mode: No-data scenarios

2. **`connection_tester.py`** - Complete diagnostics
   - Socket connectivity testing
   - xDrip service verification
   - Network interface analysis
   - Bluetooth status checking

3. **`dependency_analyzer.py`** - Build health monitoring
   - Dependency conflict detection
   - Version compatibility analysis
   - Deprecation warnings
   - Build issue diagnostics

### 🏗️ Build System
- **Modern Gradle Setup**: Version catalog, Kotlin DSL
- **Latest Dependencies**: AGP 8.13.0, Kotlin 2.2.21, Compose BOM 2025.10.01
- **Proper Configuration**: Room schema management, KAPT setup
- **Clean Warnings**: All deprecation issues resolved

## Recent Optimizations

### ✅ Dependency Issues Resolved
- Fixed Room schema configuration warnings
- Updated deprecated Kotlin configuration (jvmTarget → compilerOptions)
- Replaced deprecated Icons.Filled.ArrowBack with AutoMirrored version
- Proper WindowInsetsController setup order

### ✅ Testing Consolidation
- **90% reduction** in test file count (14+ → 3 files)
- Eliminated redundant mock servers
- Single source of truth for each testing function
- Consistent command-line interfaces

### ✅ Repository Cleanup
- Removed temporary Kotlin compiler logs
- Cleaned up redundant test servers
- Updated documentation to reflect new structure
- Enhanced .gitignore for better exclusions

## Usage Examples

### Quick Development Testing
```bash
# Start mock server
python testing/mock_server.py

# Test connections
python testing/connection_tester.py

# Check dependencies
python testing/dependency_analyzer.py
```

### Real Device Testing
```bash
# Comprehensive connection diagnostics
python testing/connection_tester.py

# Quick test specific IP
python testing/connection_tester.py --quick 192.168.44.1
```

### Build and Quality
```bash
# Clean build
./gradlew clean assembleDebug

# Check for dependency issues
python testing/dependency_analyzer.py
```

## Technology Stack

- **Frontend**: Jetpack Compose with Material 3
- **Language**: Kotlin 2.2.21
- **Build**: Gradle 8.13 with version catalog
- **Database**: Room 2.8.3 with proper schema management
- **Networking**: OkHttp 5.3.0 + Gson 2.13.2
- **Charts**: MPAndroidChart v3.1.0 (consider migration to Compose Charts)
- **Testing**: Python-based mock servers and diagnostics

## Development Workflow

1. **Code Changes**: Make changes in `app/src/main/java/`
2. **Build Verification**: `./gradlew assembleDebug`
3. **Dependency Check**: `python testing/dependency_analyzer.py`
4. **Testing**: Use mock server for emulator or connection tester for real devices
5. **Integration**: Run automated tests with `testing/run_integration_tests.bat`

This structure provides a clean, maintainable, and efficient development environment with comprehensive testing capabilities and modern Android development practices.