#!/usr/bin/env python3
"""
Simple Test Server for Glucose Data
"""

import json
import time
import random
from datetime import datetime
from http.server import HTTPServer, SimpleHTTPRequestHandler
import socketserver

class TestHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/sgv.json':
            # Generate test glucose data
            glucose_value = random.randint(80, 180)
            now = int(time.time() * 1000)
            
            glucose_data = [{
                "_id": f"test_{now}",
                "sgv": glucose_value,
                "date": now,
                "dateString": datetime.now().isoformat(),
                "trend": 4,
                "direction": "Flat",
                "device": "TestServer",
                "type": "sgv"
            }]
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            response_json = json.dumps(glucose_data, indent=2)
            self.wfile.write(response_json.encode())
            print(f"Served glucose: {glucose_value} mg/dL")
        else:
            self.send_error(404)

if __name__ == "__main__":
    PORT = 17580
    with socketserver.TCPServer(("127.0.0.1", PORT), TestHandler) as httpd:
        print(f"Test server running on http://127.0.0.1:{PORT}")
        print(f"Glucose endpoint: http://127.0.0.1:{PORT}/sgv.json")
        print("Press Ctrl+C to stop")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nStopping server...")