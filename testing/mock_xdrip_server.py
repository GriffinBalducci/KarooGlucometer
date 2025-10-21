#!/usr/bin/env python3
"""
Mock xDrip+ HTTP Server for Testing
Simulates xDrip's HTTP web service to test glucose data fetching
"""

import json
import time
import random
from datetime import datetime, timedelta
from http.server import HTTPServer, BaseHTTPRequestHandler
import threading
import argparse

class MockXDripServer(BaseHTTPRequestHandler):
    """HTTP server that mimics xDrip+ /sgv.json endpoint"""
    
    # Class variable to maintain state across requests
    glucose_value = 120
    
    def do_GET(self):
        """Handle GET requests"""
        if self.path == '/sgv.json':
            self.send_glucose_data()
        elif self.path == '/status':
            self.send_status()
        elif self.path == '/':
            self.send_index()
        else:
            self.send_error(404, "Not Found")
    
    def send_glucose_data(self):
        """Send glucose data in xDrip format"""
        # Simulate realistic glucose changes
        change = random.randint(-8, 8)  # Small realistic changes
        MockXDripServer.glucose_value = max(70, min(250, MockXDripServer.glucose_value + change))
        
        # Determine trend direction
        if change > 3:
            direction = "FortyFiveUp"
        elif change < -3:
            direction = "FortyFiveDown"
        else:
            direction = "Flat"
        
        # Create xDrip-style JSON response
        now = int(time.time() * 1000)  # xDrip uses milliseconds
        
        glucose_data = [{
            "_id": f"mock_{now}",
            "sgv": MockXDripServer.glucose_value,
            "date": now,
            "dateString": datetime.now().isoformat(),
            "trend": 4,
            "direction": direction,
            "device": "MockXDrip",
            "type": "sgv",
            "filtered": MockXDripServer.glucose_value * 1000,
            "unfiltered": MockXDripServer.glucose_value * 1000,
            "rssi": 100,
            "noise": 1,
            "sysTime": datetime.now().isoformat()
        }]
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        
        response_json = json.dumps(glucose_data, indent=2)
        self.wfile.write(response_json.encode())
        
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Served glucose: {MockXDripServer.glucose_value} mg/dL ({direction})")
    
    def send_status(self):
        """Send server status"""
        status = {
            "server": "MockXDrip",
            "version": "1.0",
            "uptime": time.time(),
            "current_glucose": MockXDripServer.glucose_value,
            "requests_served": getattr(self, 'request_count', 0)
        }
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(status, indent=2).encode())
    
    def send_index(self):
        """Send simple HTML index"""
        html = """
        <html>
        <head><title>Mock xDrip Server</title></head>
        <body>
            <h1>Mock xDrip+ HTTP Server</h1>
            <p>Endpoints:</p>
            <ul>
                <li><a href="/sgv.json">/sgv.json</a> - Glucose data (xDrip format)</li>
                <li><a href="/status">/status</a> - Server status</li>
            </ul>
            <p>This server mimics xDrip+ for testing purposes.</p>
        </body>
        </html>
        """
        
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()
        self.wfile.write(html.encode())
    
    def log_message(self, format, *args):
        """Override to customize logging"""
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {format % args}")

def run_server(port=17580, host='localhost'):
    """Run the mock xDrip server"""
    server_address = (host, port)
    httpd = HTTPServer(server_address, MockXDripServer)
    
    print(f"Mock xDrip Server starting on http://{host}:{port}")
    print(f"Glucose endpoint: http://{host}:{port}/sgv.json")
    print("Press Ctrl+C to stop")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server...")
        httpd.shutdown()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Mock xDrip+ HTTP Server')
    parser.add_argument('--port', type=int, default=17580, 
                       help='Port to run on (default: 17580)')
    parser.add_argument('--host', default='localhost',
                       help='Host to bind to (default: localhost)')
    
    args = parser.parse_args()
    run_server(args.port, args.host)