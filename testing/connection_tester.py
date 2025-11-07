#!/usr/bin/env python3
"""
KarooGlucometer Testing Suite - Consolidated Connection Tester
Single tool for all connection and Bluetooth PAN testing
"""

import socket
import subprocess
import json
import time
import requests
import argparse

class ConnectionTester:
    """Comprehensive connection testing for KarooGlucometer"""
    
    def __init__(self):
        self.bluetooth_ips = ['192.168.44.1', '192.168.45.1', '192.168.46.1']
        self.port = 17580
        
    def test_socket_connection(self, ip, port=None):
        """Test basic socket connectivity"""
        if port is None:
            port = self.port
            
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(3)
            result = sock.connect_ex((ip, port))
            sock.close()
            return result == 0
        except Exception:
            return False
    
    def test_xdrip_service(self, ip):
        """Test xDrip web service"""
        try:
            response = requests.get(f"http://{ip}:{self.port}/sgv.json", timeout=5)
            if response.status_code == 200:
                data = response.json()
                return True, data
            else:
                return False, f"HTTP {response.status_code}"
        except requests.exceptions.ConnectionError:
            return False, "Connection refused"
        except requests.exceptions.Timeout:
            return False, "Timeout"
        except Exception as e:
            return False, str(e)
    
    def ping_test(self, ip):
        """Test ping connectivity"""
        try:
            # Windows ping command
            result = subprocess.run(['ping', '-n', '2', ip], 
                                  capture_output=True, text=True, timeout=10)
            return result.returncode == 0
        except Exception:
            return False
    
    def check_bluetooth_status(self):
        """Check if Bluetooth service is running"""
        try:
            result = subprocess.run(['sc', 'query', 'bthserv'], 
                                  capture_output=True, text=True)
            return 'RUNNING' in result.stdout
        except Exception:
            return False
    
    def get_network_interfaces(self):
        """Get network interface information"""
        interfaces = []
        try:
            result = subprocess.run(['ipconfig', '/all'], 
                                  capture_output=True, text=True)
            
            current_interface = None
            for line in result.stdout.split('\n'):
                line = line.strip()
                if line and not line.startswith(' ') and ':' in line:
                    current_interface = line
                elif 'IPv4' in line and '192.168.' in line:
                    if current_interface:
                        interfaces.append({
                            'name': current_interface,
                            'ip': line.split(':')[-1].strip()
                        })
        except Exception:
            pass
        return interfaces
    
    def run_comprehensive_test(self):
        """Run comprehensive connection tests"""
        print("KarooGlucometer Connection Tester")
        print("=" * 50)
        
        # 1. Check Bluetooth service
        print("\n1. Bluetooth Service Status:")
        bt_running = self.check_bluetooth_status()
        print(f"   Bluetooth service: {'RUNNING' if bt_running else 'NOT RUNNING'}")
        
        # 2. Network interfaces
        print("\n2. Network Interfaces:")
        interfaces = self.get_network_interfaces()
        bluetooth_found = False
        for interface in interfaces:
            print(f"   {interface['name']} - {interface['ip']}")
            if any(term in interface['name'].lower() for term in ['bluetooth', 'pan', 'bnep']):
                bluetooth_found = True
                print(f"      [BLUETOOTH] Potential Bluetooth PAN interface")
        
        if not bluetooth_found:
            print("   [INFO] No obvious Bluetooth PAN interfaces found")
        
        # 3. Test common Bluetooth PAN IPs
        print(f"\n3. Testing Bluetooth PAN IP addresses:")
        working_ip = None
        for ip in self.bluetooth_ips:
            print(f"   Testing {ip}...")
            
            # Ping test
            ping_ok = self.ping_test(ip)
            print(f"      Ping: {'OK' if ping_ok else 'FAIL'}")
            
            # Socket test
            socket_ok = self.test_socket_connection(ip)
            print(f"      Socket: {'OK' if socket_ok else 'FAIL'}")
            
            # xDrip service test
            if socket_ok:
                xdrip_ok, xdrip_data = self.test_xdrip_service(ip)
                print(f"      xDrip: {'OK' if xdrip_ok else 'FAIL'}")
                if xdrip_ok:
                    if isinstance(xdrip_data, list) and len(xdrip_data) > 0:
                        sgv = xdrip_data[0].get('sgv', 'Unknown')
                        direction = xdrip_data[0].get('direction', 'Unknown')
                        print(f"         Latest: {sgv} mg/dL ({direction})")
                    working_ip = ip
            else:
                print(f"      xDrip: SKIP (no socket connection)")
        
        # 4. Test localhost for emulator testing
        print(f"\n4. Testing localhost (emulator mode):")
        localhost_socket = self.test_socket_connection('127.0.0.1')
        print(f"   Socket to 127.0.0.1:{self.port}: {'OK' if localhost_socket else 'FAIL'}")
        if localhost_socket:
            localhost_xdrip, data = self.test_xdrip_service('127.0.0.1')
            print(f"   xDrip service: {'OK' if localhost_xdrip else 'FAIL'}")
            if not working_ip and localhost_xdrip:
                working_ip = '127.0.0.1'
        
        # 5. Summary and recommendations
        print(f"\n5. Summary:")
        if working_ip:
            print(f"   [SUCCESS] Found working connection: {working_ip}")
            print(f"   [ACTION] Use IP {working_ip} in KarooGlucometer debug overlay")
            if working_ip == '127.0.0.1':
                print(f"   [NOTE] Using localhost - make sure mock server is running")
            else:
                print(f"   [NOTE] Real device connection detected")
        else:
            print(f"   [FAIL] No working connections found")
            print(f"   [ACTION] Troubleshooting needed:")
            print(f"      - Check Bluetooth pairing and tethering")
            print(f"      - Verify xDrip web service is enabled")
            print(f"      - Ensure devices are in range")
        
        return working_ip
    
    def quick_test(self, ip):
        """Quick test of a specific IP"""
        print(f"Quick test of {ip}:{self.port}")
        
        socket_ok = self.test_socket_connection(ip)
        print(f"Socket: {'OK' if socket_ok else 'FAIL'}")
        
        if socket_ok:
            xdrip_ok, data = self.test_xdrip_service(ip)
            print(f"xDrip: {'OK' if xdrip_ok else 'FAIL'}")
            if xdrip_ok and isinstance(data, list) and len(data) > 0:
                sgv = data[0].get('sgv', 'Unknown')
                print(f"Latest glucose: {sgv} mg/dL")
        
        return socket_ok

def main():
    """Main function with command line options"""
    parser = argparse.ArgumentParser(description='KarooGlucometer Connection Tester')
    parser.add_argument('--quick', help='Quick test of specific IP address')
    parser.add_argument('--port', type=int, default=17580, help='Port to test (default: 17580)')
    
    args = parser.parse_args()
    
    tester = ConnectionTester()
    tester.port = args.port
    
    if args.quick:
        tester.quick_test(args.quick)
    else:
        tester.run_comprehensive_test()

if __name__ == "__main__":
    main()