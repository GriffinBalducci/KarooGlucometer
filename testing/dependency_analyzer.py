#!/usr/bin/env python3
"""
KarooGlucometer Dependency Analysis Tool
Analyzes build files and identifies potential dependency issues
"""

import re
import json
from pathlib import Path

class DependencyAnalyzer:
    """Analyze Android project dependencies for issues"""
    
    def __init__(self, project_path=None):
        self.project_path = Path(project_path) if project_path else Path.cwd()
        self.issues = []
        self.warnings = []
        self.suggestions = []
        
    def analyze(self):
        """Run comprehensive dependency analysis"""
        print("KAROO GLUCOMETER DEPENDENCY ANALYSIS")
        print("=" * 50)
        
        # Analyze version catalog
        self.analyze_version_catalog()
        
        # Analyze app build.gradle.kts
        self.analyze_app_build_gradle()
        
        # Check for known problematic combinations
        self.check_known_issues()
        
        # Generate report
        self.generate_report()
        
    def analyze_version_catalog(self):
        """Analyze gradle/libs.versions.toml for issues"""
        toml_path = self.project_path / "gradle" / "libs.versions.toml"
        
        if not toml_path.exists():
            self.issues.append("libs.versions.toml not found - using legacy dependency management")
            return
            
        try:
            content = toml_path.read_text(encoding='utf-8')
            
            # Check version patterns
            self.check_version_patterns(content)
            
            # Check for compatibility issues
            self.check_compose_compatibility(content)
            
            # Check Room/Kotlin compatibility
            self.check_room_kotlin_compatibility(content)
            
        except Exception as e:
            self.issues.append(f"Failed to read libs.versions.toml: {e}")
    
    def check_version_patterns(self, content):
        """Check for problematic version patterns"""
        
        # Extract versions
        versions = {}
        version_section = False
        
        for line in content.split('\n'):
            line = line.strip()
            
            if line == '[versions]':
                version_section = True
                continue
            elif line.startswith('[') and line != '[versions]':
                version_section = False
                continue
                
            if version_section and '=' in line:
                key, value = line.split('=', 1)
                key = key.strip()
                value = value.strip().strip('"')
                versions[key] = value
        
        # Check for potential issues
        if 'agp' in versions:
            agp_version = versions['agp']
            if agp_version.startswith('8.'):
                print(f"✓ AGP version {agp_version} (compatible)")
            else:
                self.warnings.append(f"AGP version {agp_version} may have compatibility issues with Kotlin 2.x")
        
        if 'kotlin' in versions:
            kotlin_version = versions['kotlin']
            if kotlin_version.startswith('2.'):
                print(f"✓ Kotlin version {kotlin_version} (modern)")
            else:
                self.warnings.append(f"Kotlin version {kotlin_version} is outdated")
        
        if 'composeBom' in versions:
            compose_bom = versions['composeBom']
            print(f"✓ Compose BOM version {compose_bom}")
            
        # Check for version conflicts
        self.versions = versions
    
    def check_compose_compatibility(self, content):
        """Check Compose version compatibility"""
        
        # Check for BOM usage
        if 'compose-bom' not in content:
            self.issues.append("Not using Compose BOM - may lead to version conflicts")
            self.suggestions.append("Add Compose BOM to ensure compatible versions")
        
        # Check for explicit Compose versions
        if re.search(r'androidx\.compose\..*:\d+\.\d+\.\d+', content):
            self.warnings.append("Explicit Compose versions found - BOM should handle versioning")
            
    def check_room_kotlin_compatibility(self, content):
        """Check Room and Kotlin compatibility"""
        
        if 'room' in content and 'kotlin' in content:
            # Room 2.8+ requires specific Kotlin versions
            if hasattr(self, 'versions'):
                room_version = self.versions.get('room', '')
                kotlin_version = self.versions.get('kotlin', '')
                
                if room_version.startswith('2.8') and not kotlin_version.startswith('2.'):
                    self.issues.append(f"Room {room_version} requires Kotlin 2.x, but using Kotlin {kotlin_version}")
    
    def analyze_app_build_gradle(self):
        """Analyze app/build.gradle.kts"""
        build_gradle_path = self.project_path / "app" / "build.gradle.kts"
        
        if not build_gradle_path.exists():
            self.issues.append("app/build.gradle.kts not found")
            return
            
        try:
            content = build_gradle_path.read_text(encoding='utf-8')
            
            # Check for KAPT usage with Room
            if 'kapt' in content and 'room' in content:
                print("✓ Using KAPT for Room annotation processing")
            elif 'room' in content and 'kapt' not in content:
                self.warnings.append("Room requires KAPT for annotation processing")
                
            # Check target SDK
            target_sdk_match = re.search(r'targetSdk\s*=\s*(\d+)', content)
            if target_sdk_match:
                target_sdk = int(target_sdk_match.group(1))
                if target_sdk >= 34:
                    print(f"✓ Target SDK {target_sdk} (modern)")
                else:
                    self.warnings.append(f"Target SDK {target_sdk} is outdated")
            
            # Check compile SDK
            compile_sdk_match = re.search(r'compileSdk\s*=\s*(\d+)', content)
            if compile_sdk_match:
                compile_sdk = int(compile_sdk_match.group(1))
                if compile_sdk >= 34:
                    print(f"✓ Compile SDK {compile_sdk} (modern)")
                    
            # Check for deprecated dependencies
            self.check_deprecated_dependencies(content)
            
        except Exception as e:
            self.issues.append(f"Failed to read app/build.gradle.kts: {e}")
    
    def check_deprecated_dependencies(self, content):
        """Check for deprecated dependencies"""
        
        deprecated_patterns = [
            (r'androidx\.compose\.material:', 'Consider migrating to Material 3'),
            (r'androidx\.navigation:navigation-compose:\d+\.\d+\.\d+', 'Use version catalog instead'),
            (r'implementation\s*\(\s*"[^"]*"\s*\)', 'Consider using version catalog'),
        ]
        
        for pattern, message in deprecated_patterns:
            if re.search(pattern, content):
                self.suggestions.append(message)
    
    def check_known_issues(self):
        """Check for known problematic dependency combinations"""
        
        # MPAndroidChart compatibility
        self.warnings.append("MPAndroidChart v3.1.0 may have issues with newer Compose versions")
        self.suggestions.append("Consider migrating to Compose-native charting library")
        
        # OkHttp version check
        if hasattr(self, 'versions') and 'okhttp' in self.versions:
            okhttp_version = self.versions['okhttp']
            if okhttp_version.startswith('5.'):
                print(f"✓ OkHttp {okhttp_version} (modern)")
            else:
                self.warnings.append(f"OkHttp {okhttp_version} may have compatibility issues")
        
        # Gson compatibility
        if hasattr(self, 'versions') and 'gson' in self.versions:
            gson_version = self.versions['gson']
            print(f"✓ Gson {gson_version}")
    
    def generate_report(self):
        """Generate comprehensive analysis report"""
        
        print("\n" + "=" * 50)
        print("DEPENDENCY ANALYSIS REPORT")
        print("=" * 50)
        
        if self.issues:
            print("\n❌ CRITICAL ISSUES:")
            for i, issue in enumerate(self.issues, 1):
                print(f"{i}. {issue}")
        else:
            print("\n✅ No critical dependency issues found")
        
        if self.warnings:
            print("\n⚠️  WARNINGS:")
            for i, warning in enumerate(self.warnings, 1):
                print(f"{i}. {warning}")
        else:
            print("\n✅ No dependency warnings")
            
        if self.suggestions:
            print("\n💡 SUGGESTIONS:")
            for i, suggestion in enumerate(self.suggestions, 1):
                print(f"{i}. {suggestion}")
        
        # Specific recommendations
        print("\n🔧 SPECIFIC RECOMMENDATIONS:")
        print("1. Room Schema Location: Add 'androidx.room' plugin or set exportSchema = false")
        print("2. Deprecated APIs: Update statusBarColor usage to WindowInsetsController")
        print("3. Icon Usage: Replace Icons.Filled.ArrowBack with Icons.AutoMirrored.Filled.ArrowBack")
        print("4. MPAndroidChart: Consider migration to Compose Charts for better integration")
        
        print("\n📊 DEPENDENCY SUMMARY:")
        if hasattr(self, 'versions'):
            for key, value in self.versions.items():
                print(f"  {key}: {value}")
        
        # Build status
        print(f"\n🏗️  BUILD STATUS: {'✅ Successful' if not self.issues else '❌ Has Issues'}")

def main():
    """Main function"""
    analyzer = DependencyAnalyzer()
    analyzer.analyze()

if __name__ == "__main__":
    main()