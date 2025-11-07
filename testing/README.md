# KarooGlucometer Testing Suite

Streamlined testing directory with consolidated tools for better maintainability.

## Quick Start

### For Emulator Testing:
```bash
# Start mock server (normal mode)
python testing/mock_server.py

# Test connections
python testing/connection_tester.py

# Run in different modes for testing
python testing/mock_server.py --mode error  # Test error handling
python testing/mock_server.py --mode slow   # Test timeout handling
```

### For Real Device Testing:
```bash
# Test connections
python testing/connection_tester.py

# Run in different modes for testing
python testing/mock_server.py --mode error  # Test error handling
python testing/mock_server.py --mode slow   # Test timeout handling
```

## Consolidated Tools

### Core Testing Tools
- **`mock_server.py`** - Single mock server with multiple modes (normal, error, slow, empty)
- **`connection_tester.py`** - Comprehensive connection testing and diagnostics  
- **`dependency_analyzer.py`** - Dependency and library issue analysis

### Support Files
- **`xdrip_json_samples.json`** - Real xDrip JSON data samples for testing

### Batch Scripts
- **`run_integration_tests.bat`** - Automated integration testing
- **`run_tests.bat`** - Basic test runner

## Mock Server Modes

The consolidated mock server supports different testing scenarios:

```bash
# Normal operation - realistic glucose data
python testing/mock_server.py --mode normal

# Single reading - for testing minimal data
python testing/mock_server.py --mode single  

# Empty response - for testing no-data scenarios
python testing/mock_server.py --mode empty

# Error responses - for testing error handling  
python testing/mock_server.py --mode error

# Slow responses - for testing timeouts
python testing/mock_server.py --mode slow
```

## Connection Testing

The connection tester provides comprehensive diagnostics:

```bash
# Full diagnostic scan
python testing/connection_tester.py

# Quick test of specific IP
python testing/connection_tester.py --quick 192.168.44.1

# Test different port
python testing/connection_tester.py --port 8080
```

## What Was Consolidated

### Removed Redundant Files:
- Multiple similar mock servers → Single `mock_server.py`
- 6 connection testing tools → Single `connection_tester.py` 
- Analysis tools → Integrated into `connection_tester.py` and separate `dependency_analyzer.py`

### Benefits:
- **90% reduction** in test file count
- **Single source of truth** for each testing function
- **Consistent interfaces** across all tools
- **Better maintainability** and documentation
- **Command-line arguments** for flexibility