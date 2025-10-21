#!/usr/bin/env python3
"""
Network Failure Test Server
Simulates various network conditions and failures
"""

import json
import time
import random
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
import threading
import argparse

class NetworkFailureServer(BaseHTTPRequestHandler):
    """Server that simulates network issues"""
    
    def __init__(self, *args, **kwargs):
        self.glucose_value = 120
        self.request_count = 0
        super().__init__(*args, **kwargs)
    
    def do_GET(self):
        """Handle GET requests with various failure modes"""
        self.request_count += 1
        
        if self.path == '/sgv.json':
            self.simulate_network_conditions()
        elif self.path == '/timeout':
            self.simulate_timeout()
        elif self.path == '/malformed':
            self.send_malformed_json()
        elif self.path == '/empty':
            self.send_empty_response()
        elif self.path == '/status':
            self.send_status()
        else:
            self.send_error(404, "Not Found")
    
    def simulate_network_conditions(self):
        """Simulate various real-world network conditions"""
        scenario = random.choice([
            'success',      # 50% success
            'success',
            'timeout',      # 20% timeout
            'server_error', # 15% server errors
            'malformed',    # 10% malformed data
            'slow'          # 5% slow response
        ])
        
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Request #{self.request_count}: {scenario}")
        
        if scenario == 'timeout':
            # Simulate timeout by delaying response
            time.sleep(10)  # This will cause client timeout
            return
        
        elif scenario == 'server_error':
            self.send_error(500, "Internal Server Error")
            return
        
        elif scenario == 'malformed':
            self.send_malformed_json()
            return
        
        elif scenario == 'slow':
            # Slow but successful response
            time.sleep(2)
        
        # Send normal glucose data
        self.send_normal_glucose()
    
    def simulate_timeout(self):
        """Deliberately timeout to test client timeout handling"""
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Simulating timeout...")
        time.sleep(30)  # Long delay to force timeout
    
    def send_malformed_json(self):
        """Send malformed JSON to test error handling"""
        malformed_responses = [
            '{"sgv": "not_a_number", "date": 123}',  # Invalid glucose value
            '{"sgv": 120, "date": "not_a_date"}',    # Invalid date
            '{"missing_sgv": 120}',                  # Missing required field
            '{"sgv": null}',                         # Null glucose value
            '{invalid json syntax',                  # Broken JSON
            '',                                      # Empty response
            'null',                                  # JSON null
            '[]'                                     # Empty array
        ]
        
        response = random.choice(malformed_responses)
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(response.encode())
        
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Sent malformed: {response[:50]}...")
    
    def send_empty_response(self):
        """Send completely empty response"""
        self.send_response(204)  # No content
        self.end_headers()
    
    def send_normal_glucose(self):
        """Send normal glucose data"""
        change = random.randint(-5, 5)
        self.glucose_value = max(70, min(250, self.glucose_value + change))
        
        glucose_data = [{
            "_id": f"test_{int(time.time() * 1000)}",
            "sgv": self.glucose_value,
            "date": int(time.time() * 1000),
            "dateString": datetime.now().isoformat(),
            "direction": "Flat",
            "device": "FailureTestServer"
        }]
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(glucose_data).encode())
    
    def send_status(self):
        """Send server status"""
        status = {
            "server": "NetworkFailureTestServer",
            "requests_served": self.request_count,
            "current_glucose": self.glucose_value,
            "test_mode": "failure_simulation"
        }
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(status, indent=2).encode())

def run_failure_server(port=17581, host='localhost'):
    """Run the network failure test server"""
    server_address = (host, port)
    httpd = HTTPServer(server_address, NetworkFailureServer)
    
    print(f"Network Failure Test Server starting on http://{host}:{port}")
    print("This server simulates various network failure conditions:")
    print("• Random timeouts")
    print("• Server errors (500)")
    print("• Malformed JSON responses")
    print("• Slow responses")
    print("• Empty responses")
    print()
    print("Endpoints:")
    print(f"• http://{host}:{port}/sgv.json - Random failures")
    print(f"• http://{host}:{port}/timeout - Guaranteed timeout")
    print(f"• http://{host}:{port}/malformed - Malformed JSON")
    print(f"• http://{host}:{port}/empty - Empty response")
    print()
    print("Press Ctrl+C to stop")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down failure test server...")
        httpd.shutdown()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Network Failure Test Server')
    parser.add_argument('--port', type=int, default=17581, 
                       help='Port to run on (default: 17581)')
    parser.add_argument('--host', default='localhost',
                       help='Host to bind to (default: localhost)')
    
    args = parser.parse_args()
    run_failure_server(args.port, args.host)