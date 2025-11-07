#!/usr/bin/env python3
"""
KarooGlucometer Testing Suite - Consolidated Test Server
Single mock server for all testing scenarios
"""

import json
import time
import random
import threading
import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler

class MockXDripHandler(BaseHTTPRequestHandler):
    """HTTP request handler that simulates xDrip responses"""
    
    def __init__(self, *args, server_mode="normal", **kwargs):
        self.server_mode = server_mode
        super().__init__(*args, **kwargs)
    
    def do_GET(self):
        """Handle GET requests"""
        if self.path == '/sgv.json':
            self.send_glucose_data()
        elif self.path == '/status.json':
            self.send_status()
        elif self.path == '/fail':
            self.send_error()
        else:
            self.send_404()
    
    def send_glucose_data(self):
        """Send mock glucose data based on server mode"""
        if self.server.server_mode == "error":
            self.send_error()
            return
        elif self.server.server_mode == "slow":
            time.sleep(5)  # Simulate slow response
        elif self.server.server_mode == "empty":
            glucose_data = []
        else:
            # Generate realistic glucose readings
            glucose_data = []
            num_readings = 1 if self.server.server_mode == "single" else 3
            
            for i in range(num_readings):
                base_time = int(time.time() * 1000) - (i * 300000)  # 5 min intervals
                glucose_value = random.randint(80, 180)
                trend = random.choice([3, 4, 4, 4, 5])  # Mostly flat
                
                directions = {
                    1: "DoubleUp", 2: "SingleUp", 3: "FortyFiveUp",
                    4: "Flat", 5: "FortyFiveDown", 6: "SingleDown", 7: "DoubleDown"
                }
                
                reading = {
                    "_id": f"test_{base_time}_{i}",
                    "sgv": glucose_value,
                    "date": base_time,
                    "dateString": time.strftime("%Y-%m-%dT%H:%M:%S.000Z", time.gmtime(base_time/1000)),
                    "trend": trend,
                    "direction": directions[trend],
                    "device": "MockSensor",
                    "type": "sgv"
                }
                glucose_data.append(reading)
        
        response = json.dumps(glucose_data, indent=2)
        
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response.encode('utf-8'))
        
        print(f"[{time.strftime('%H:%M:%S')}] Served glucose data ({len(glucose_data)} readings)")
    
    def send_status(self):
        """Send mock status information"""
        status_data = {
            "status": "ok",
            "version": "mock-server-1.0",
            "timestamp": int(time.time() * 1000),
            "mode": self.server.server_mode
        }
        
        response = json.dumps(status_data, indent=2)
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response.encode('utf-8'))
    
    def send_error(self):
        """Send error response"""
        self.send_response(500)
        self.send_header('Content-Type', 'text/plain')
        self.end_headers()
        self.wfile.write(b'Internal Server Error')
    
    def send_404(self):
        """Send 404 response"""
        self.send_response(404)
        self.send_header('Content-Type', 'text/plain')
        self.end_headers()
        self.wfile.write(b'Not Found')
    
    def log_message(self, format, *args):
        """Override to provide cleaner logging"""
        pass

class MockXDripServer:
    """Consolidated mock xDrip server for all testing scenarios"""
    
    def __init__(self, host='127.0.0.1', port=17580, mode="normal"):
        self.host = host
        self.port = port
        self.mode = mode
        self.server = None
        self.thread = None
        self.running = False
    
    def start(self):
        """Start the mock server"""
        try:
            # Create a custom handler class with the mode
            def handler_factory(*args, **kwargs):
                return MockXDripHandler(*args, server_mode=self.mode, **kwargs)
            
            self.server = HTTPServer((self.host, self.port), handler_factory)
            self.server.server_mode = self.mode  # Store mode on server
            
            self.thread = threading.Thread(target=self.server.serve_forever)
            self.thread.daemon = True
            self.thread.start()
            self.running = True
            
            print(f"Mock xDrip server started on {self.host}:{self.port}")
            print(f"Mode: {self.mode}")
            print(f"Available endpoints:")
            print(f"  http://{self.host}:{self.port}/sgv.json - Glucose data")
            print(f"  http://{self.host}:{self.port}/status.json - Server status")
            print(f"  http://{self.host}:{self.port}/fail - Force error response")
            
        except Exception as e:
            print(f"Failed to start server: {e}")
            return False
        
        return True
    
    def stop(self):
        """Stop the mock server"""
        if self.server:
            self.running = False
            self.server.shutdown()
            self.server.server_close()
            print("Mock xDrip server stopped")

def main():
    """Main function with command line options"""
    parser = argparse.ArgumentParser(description='Mock xDrip Server for KarooGlucometer Testing')
    parser.add_argument('--host', default='127.0.0.1', help='Host to bind to (default: 127.0.0.1)')
    parser.add_argument('--port', type=int, default=17580, help='Port to bind to (default: 17580)')
    parser.add_argument('--mode', choices=['normal', 'single', 'empty', 'error', 'slow'], 
                       default='normal', help='Server mode for testing different scenarios')
    
    args = parser.parse_args()
    
    print("Mock xDrip Server for KarooGlucometer Testing")
    print("=" * 50)
    print(f"Starting server on {args.host}:{args.port} in '{args.mode}' mode")
    print("\nAvailable modes:")
    print("  normal - Multiple realistic glucose readings")
    print("  single - Single glucose reading")
    print("  empty  - Empty response array")
    print("  error  - Always return HTTP 500 error")
    print("  slow   - Simulate slow network (5s delay)")
    print("\nPress Ctrl+C to stop")
    
    server = MockXDripServer(args.host, args.port, args.mode)
    
    if server.start():
        try:
            while server.running:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\nShutting down...")
        finally:
            server.stop()
    else:
        print("Failed to start server")

if __name__ == "__main__":
    main()