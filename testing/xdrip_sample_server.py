#!/usr/bin/env python3
"""
Real xDrip JSON Sample Server
Serves actual xDrip+ JSON formats for compatibility testing
"""

import json
import time
import random
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
import os

class XDripSampleServer(BaseHTTPRequestHandler):
    """Server that serves real xDrip JSON samples"""
    
    def __init__(self, *args, **kwargs):
        # Load real xDrip samples
        samples_file = os.path.join(os.path.dirname(__file__), 'xdrip_json_samples.json')
        with open(samples_file, 'r') as f:
            self.samples = json.load(f)
        
        self.current_sample = 'typical_reading'
        super().__init__(*args, **kwargs)
    
    def do_GET(self):
        """Handle GET requests"""
        if self.path == '/sgv.json':
            self.serve_sample()
        elif self.path.startswith('/sample/'):
            sample_name = self.path.split('/')[-1]
            self.serve_specific_sample(sample_name)
        elif self.path == '/samples':
            self.list_samples()
        elif self.path == '/cycle':
            self.cycle_samples()
        elif self.path == '/':
            self.send_index()
        else:
            self.send_error(404, "Not Found")
    
    def serve_sample(self):
        """Serve the current sample"""
        sample_data = self.samples['samples'][self.current_sample]
        
        # Update timestamps to current time for realism
        updated_data = self.update_timestamps(sample_data)
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        
        response_json = json.dumps(updated_data, indent=2)
        self.wfile.write(response_json.encode())
        
        sample_sgv = updated_data[0].get('sgv', 'N/A') if updated_data else 'N/A'
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Served {self.current_sample}: {sample_sgv} mg/dL")
    
    def serve_specific_sample(self, sample_name):
        """Serve a specific sample by name"""
        if sample_name in self.samples['samples']:
            sample_data = self.samples['samples'][sample_name]
            updated_data = self.update_timestamps(sample_data)
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(updated_data, indent=2).encode())
            
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Served specific sample: {sample_name}")
        else:
            self.send_error(404, f"Sample '{sample_name}' not found")
    
    def update_timestamps(self, data):
        """Update timestamps to current time for realistic testing"""
        updated = json.loads(json.dumps(data))  # Deep copy
        current_time = int(time.time() * 1000)
        
        if isinstance(updated, list):
            for i, item in enumerate(updated):
                if 'date' in item:
                    # Spread readings 5 minutes apart
                    item['date'] = current_time - (i * 5 * 60 * 1000)
                if 'dateString' in item:
                    item_time = datetime.fromtimestamp((current_time - (i * 5 * 60 * 1000)) / 1000)
                    item['dateString'] = item_time.isoformat() + 'Z'
        
        return updated
    
    def list_samples(self):
        """List all available samples"""
        sample_list = {
            'available_samples': list(self.samples['samples'].keys()),
            'current_sample': self.current_sample,
            'usage': {
                '/sgv.json': 'Get current sample',
                '/sample/{name}': 'Get specific sample',
                '/cycle': 'Cycle through samples automatically',
                '/samples': 'List all samples'
            }
        }
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(sample_list, indent=2).encode())
    
    def cycle_samples(self):
        """Enable automatic cycling through samples"""
        sample_names = list(self.samples['samples'].keys())
        current_index = sample_names.index(self.current_sample)
        next_index = (current_index + 1) % len(sample_names)
        self.current_sample = sample_names[next_index]
        
        response = {
            'message': f'Switched to sample: {self.current_sample}',
            'current_sample': self.current_sample,
            'next_request_will_serve': self.current_sample
        }
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(response, indent=2).encode())
        
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Switched to sample: {self.current_sample}")
    
    def send_index(self):
        """Send HTML index with sample information"""
        html = f"""
        <html>
        <head><title>xDrip Sample Server</title></head>
        <body>
            <h1>Real xDrip+ JSON Sample Server</h1>
            
            <h2>Current Sample: {self.current_sample}</h2>
            
            <h3>Available Endpoints:</h3>
            <ul>
                <li><a href="/sgv.json">/sgv.json</a> - Current sample data</li>
                <li><a href="/samples">/samples</a> - List all samples</li>
                <li><a href="/cycle">/cycle</a> - Switch to next sample</li>
            </ul>
            
            <h3>Specific Samples:</h3>
            <ul>
        """
        
        for sample_name in self.samples['samples'].keys():
            html += f'<li><a href="/sample/{sample_name}">/sample/{sample_name}</a></li>'
        
        html += """
            </ul>
            
            <h3>Testing Scenarios:</h3>
            <ul>
        """
        
        for scenario, info in self.samples['test_scenarios'].items():
            if scenario != 'description':
                html += f'<li><strong>{scenario}:</strong> {info.get("expected", "")}</li>'
        
        html += """
            </ul>
            
            <p>This server provides real xDrip+ JSON formats for comprehensive compatibility testing.</p>
        </body>
        </html>
        """
        
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()
        self.wfile.write(html.encode())

def run_sample_server(port=17582, host='localhost'):
    """Run the xDrip sample server"""
    server_address = (host, port)
    httpd = HTTPServer(server_address, XDripSampleServer)
    
    print(f"xDrip Sample Server starting on http://{host}:{port}")
    print("This server provides real xDrip+ JSON formats for testing")
    print()
    print("Available samples:")
    
    # Load and display available samples
    samples_file = os.path.join(os.path.dirname(__file__), 'xdrip_json_samples.json')
    with open(samples_file, 'r') as f:
        samples = json.load(f)
    
    for sample_name in samples['samples'].keys():
        print(f"  • {sample_name}")
    
    print()
    print(f"Main endpoint: http://{host}:{port}/sgv.json")
    print(f"Web interface: http://{host}:{port}/")
    print("Press Ctrl+C to stop")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down sample server...")
        httpd.shutdown()

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description='xDrip Sample Server')
    parser.add_argument('--port', type=int, default=17582, 
                       help='Port to run on (default: 17582)')
    parser.add_argument('--host', default='localhost',
                       help='Host to bind to (default: localhost)')
    
    args = parser.parse_args()
    run_sample_server(args.port, args.host)