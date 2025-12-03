#!/usr/bin/env python3
"""
RelayPrint REST API & Web Dashboard

This Flask API is accessed via Home Assistant's Ingress proxy, which handles:
- Authentication (HA user sessions or long-lived access tokens)
- HTTPS/SSL termination
- Access control

For external/mobile app access, use HA's long-lived access tokens.
"""
from flask import Flask, request, jsonify, send_file, render_template, send_from_directory
from flask_cors import CORS
from werkzeug.utils import secure_filename
import os
import logging
import subprocess
import socket
from datetime import datetime, timezone
from job_queue_manager import queue_manager
from printer_discovery import get_printers, PrinterDiscovery

# App configuration
TEMPLATE_DIR = '/usr/local/share/relayprint/templates'
STATIC_DIR = '/usr/local/share/relayprint/static'

app = Flask(__name__,
            template_folder=TEMPLATE_DIR,
            static_folder=STATIC_DIR,
            static_url_path='/static')
CORS(app)

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

UPLOAD_FOLDER = os.environ.get('UPLOAD_FOLDER', '/data/print_jobs')
ALLOWED_EXTENSIONS = {'pdf', 'ps', 'txt', 'png', 'jpg', 'jpeg'}
MAX_CONTENT_LENGTH = 50 * 1024 * 1024  # 50MB max file size

app.config['MAX_CONTENT_LENGTH'] = MAX_CONTENT_LENGTH
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

# ============================================================================
# Web Dashboard Routes
# ============================================================================

@app.route('/')
def dashboard():
    """Serve the main dashboard UI."""
    return render_template('index.html')

# ============================================================================
# Print Job API Endpoints
# ============================================================================

@app.route('/api/print', methods=['POST'])
def submit_print_job():
    """Submit a print job.

    Authentication is handled by Home Assistant Ingress proxy.
    For mobile apps, use HA long-lived access tokens.
    """
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No file selected'}), 400

    if not allowed_file(file.filename):
        return jsonify({'error': 'File type not allowed'}), 400

    printer_name = request.form.get('printer_name')
    if not printer_name:
        return jsonify({'error': 'Printer name is required'}), 400

    try:
        # Save file temporarily
        filename = secure_filename(file.filename)
        filepath = os.path.join(UPLOAD_FOLDER, filename)
        file.save(filepath)

        # Submit print job
        options = {}
        if 'copies' in request.form:
            options['copies'] = int(request.form['copies'])
        if 'duplex' in request.form:
            options['sides'] = 'two-sided-long-edge' if request.form['duplex'] == 'true' else 'one-sided'
        if 'quality' in request.form:
            options['print-quality'] = request.form['quality']

        job_id = queue_manager.submit_job(printer_name, filepath, options)

        # Clean up file after submission
        os.unlink(filepath)

        return jsonify({
            'job_id': job_id,
            'status': 'submitted',
            'message': 'Print job submitted successfully'
        })

    except Exception as e:
        logger.error(f"Error submitting print job: {str(e)}")
        if os.path.exists(filepath):
            os.unlink(filepath)
        return jsonify({'error': str(e)}), 500

@app.route('/api/print/<int:job_id>/status', methods=['GET'])
def get_job_status(job_id):
    """Get status of a print job."""
    try:
        status = queue_manager.get_job_status(job_id)
        return jsonify(status)
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/print/<int:job_id>', methods=['DELETE'])
def cancel_job(job_id):
    """Cancel a print job."""
    try:
        success = queue_manager.cancel_job(job_id)
        if success:
            return jsonify({'message': 'Job canceled successfully'})
        return jsonify({'error': 'Failed to cancel job'}), 500
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# Printer Management API Endpoints
# ============================================================================

@app.route('/api/printers', methods=['GET'])
def list_printers():
    """List all available printers configured in CUPS."""
    try:
        printers = get_printers()
        return jsonify({'printers': printers})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/discover', methods=['GET'])
def discover_printers():
    """Discover network printers using mDNS/DNS-SD (Avahi)."""
    try:
        discovered = discover_network_printers()
        return jsonify({'printers': discovered})
    except Exception as e:
        logger.error(f"Error discovering printers: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/printers/add', methods=['POST'])
def add_printer():
    """Add a new printer to CUPS."""
    try:
        data = request.get_json()
        uri = data.get('uri')
        name = data.get('name', 'NetworkPrinter')
        location = data.get('location', '')

        if not uri:
            return jsonify({'error': 'Printer URI is required'}), 400

        # Decode mDNS escaped names first (e.g., \032 -> space)
        decoded_name = decode_mdns_name(name)

        # Sanitize printer name (CUPS doesn't like spaces or special chars)
        safe_name = ''.join(c if c.isalnum() or c in '-_' else '_' for c in decoded_name)

        result = add_printer_to_cups(safe_name, uri, location)

        if result['success']:
            return jsonify({
                'message': f'Printer {safe_name} added successfully',
                'printer_name': safe_name
            })
        else:
            return jsonify({'error': result['error']}), 500

    except Exception as e:
        logger.error(f"Error adding printer: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/printers/<printer_name>', methods=['DELETE'])
def remove_printer(printer_name):
    """Remove a printer from CUPS."""
    try:
        result = remove_printer_from_cups(printer_name)

        if result['success']:
            return jsonify({'message': f'Printer {printer_name} removed successfully'})
        else:
            return jsonify({'error': result['error']}), 500

    except Exception as e:
        logger.error(f"Error removing printer: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/printers/test', methods=['POST'])
def test_print():
    """Send a test page to a printer."""
    try:
        data = request.get_json()
        printer_name = data.get('printer_name')

        if not printer_name:
            return jsonify({'error': 'Printer name is required'}), 400

        result = send_test_page(printer_name)

        if result['success']:
            return jsonify({'message': 'Test page sent successfully', 'job_id': result.get('job_id')})
        else:
            return jsonify({'error': result['error']}), 500

    except Exception as e:
        logger.error(f"Error sending test page: {str(e)}")
        return jsonify({'error': str(e)}), 500

# ============================================================================
# Queue Status API
# ============================================================================

@app.route('/api/queue/status', methods=['GET'])
def get_queue_status():
    """Get overall queue status."""
    try:
        status = queue_manager.get_queue_status()
        return jsonify(status)
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# Health Check
# ============================================================================

@app.route('/api/health', methods=['GET'])
def health_check():
    """API health check endpoint."""
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now(timezone.utc).isoformat(),
        'version': '0.1.10'
    })

# ============================================================================
# Helper Functions
# ============================================================================

def decode_mdns_name(name):
    """Decode mDNS escaped names (e.g., \\032 -> space).

    Avahi/mDNS uses octal escape sequences for special characters.
    For example: HP\\032LaserJet -> HP LaserJet (\\032 is octal for space)
    """
    import re
    def replace_octal(match):
        return chr(int(match.group(1), 8))
    return re.sub(r'\\(\d{3})', replace_octal, name)

def discover_network_printers():
    """Discover printers on the network using avahi-browse.

    Groups printers by IP address and returns all available protocols
    for each physical printer to allow user to choose.
    """
    # Use dict to group by IP address
    printers_by_ip = {}

    # Get existing printers to filter out already-added ones
    existing_printers = get_printers()
    existing_ips = set()
    for p in existing_printers:
        uri = p.get('uri', '')
        # Extract IP from URI like ipp://192.168.1.100:631/...
        import re
        ip_match = re.search(r'://([0-9.]+)[:/]', uri)
        if ip_match:
            existing_ips.add(ip_match.group(1))

    try:
        # Use avahi-browse to find IPP printers
        result = subprocess.run(
            ['avahi-browse', '-t', '-r', '-p', '_ipp._tcp'],
            capture_output=True,
            text=True,
            timeout=10
        )

        # Parse avahi-browse output
        for line in result.stdout.split('\n'):
            if not line or line.startswith('+'):
                continue

            parts = line.split(';')
            if len(parts) >= 8 and parts[0] == '=':
                # Format: =;interface;protocol;name;type;domain;hostname;address;port;txt
                interface = parts[1]
                name = decode_mdns_name(parts[3])
                service_type = parts[4]
                hostname = decode_mdns_name(parts[6])
                address = parts[7]
                port = parts[8] if len(parts) > 8 else '631'
                txt_record = parts[9] if len(parts) > 9 else ''

                # Skip if already in CUPS
                if address in existing_ips:
                    continue

                # Build printer URI
                uri = f"ipp://{address}:{port}/ipp/print"

                # Group by IP address
                if address not in printers_by_ip:
                    printers_by_ip[address] = {
                        'name': name,
                        'hostname': hostname,
                        'address': address,
                        'protocols': []
                    }

                # Add this protocol option
                printers_by_ip[address]['protocols'].append({
                    'type': 'IPP',
                    'uri': uri,
                    'port': port,
                    'secure': False,
                    'interface': interface,
                    'txt': txt_record
                })

        # Also try to find AirPrint/IPPS printers
        result_airprint = subprocess.run(
            ['avahi-browse', '-t', '-r', '-p', '_ipps._tcp'],
            capture_output=True,
            text=True,
            timeout=10
        )

        for line in result_airprint.stdout.split('\n'):
            if not line or line.startswith('+'):
                continue

            parts = line.split(';')
            if len(parts) >= 8 and parts[0] == '=':
                interface = parts[1]
                name = decode_mdns_name(parts[3])
                hostname = decode_mdns_name(parts[6])
                address = parts[7]
                port = parts[8] if len(parts) > 8 else '631'
                txt_record = parts[9] if len(parts) > 9 else ''

                # Skip if already in CUPS
                if address in existing_ips:
                    continue

                uri = f"ipps://{address}:{port}/ipp/print"

                # Group by IP address
                if address not in printers_by_ip:
                    printers_by_ip[address] = {
                        'name': name,
                        'hostname': hostname,
                        'address': address,
                        'protocols': []
                    }

                # Add this protocol option (avoid duplicates from multiple interfaces)
                if not any(p['uri'] == uri for p in printers_by_ip[address]['protocols']):
                    printers_by_ip[address]['protocols'].append({
                        'type': 'IPPS (AirPrint)',
                        'uri': uri,
                        'port': port,
                        'secure': True,
                        'interface': interface,
                        'txt': txt_record
                    })

    except subprocess.TimeoutExpired:
        logger.warning("Printer discovery timed out")
    except FileNotFoundError:
        logger.warning("avahi-browse not available, trying alternative discovery")
        # Fallback: try to use lpinfo
        try:
            result = subprocess.run(
                ['lpinfo', '-v'],
                capture_output=True,
                text=True,
                timeout=30
            )
            for line in result.stdout.split('\n'):
                if 'network' in line and ('ipp://' in line or 'socket://' in line):
                    parts = line.split()
                    if len(parts) >= 2:
                        uri = parts[1]
                        # Extract IP from URI for grouping
                        import re
                        ip_match = re.search(r'://([0-9.]+)[:/]', uri)
                        address = ip_match.group(1) if ip_match else uri
                        if address not in printers_by_ip:
                            printers_by_ip[address] = {
                                'name': 'Network Printer',
                                'hostname': '',
                                'address': address,
                                'protocols': [{
                                    'type': 'Discovered',
                                    'uri': uri,
                                    'port': '631',
                                    'secure': uri.startswith('ipps'),
                                    'interface': '',
                                    'txt': ''
                                }]
                            }
        except Exception as e:
            logger.error(f"Fallback discovery failed: {e}")
    except Exception as e:
        logger.error(f"Printer discovery error: {e}")

    # Convert grouped dict to list for API response
    return list(printers_by_ip.values())

def get_cups_admin_credentials():
    """Get CUPS admin credentials from addon options or environment."""
    import json

    # Try to read from HA addon options file
    options_file = '/data/options.json'
    try:
        with open(options_file, 'r') as f:
            options = json.load(f)
            return (
                options.get('cups_admin_user', 'admin'),
                options.get('cups_admin_password', 'changeme')
            )
    except Exception as e:
        logger.warning(f"Could not read addon options: {e}")

    # Fall back to environment variables or defaults
    return (
        os.environ.get('CUPS_ADMIN_USER', 'admin'),
        os.environ.get('CUPS_ADMIN_PASSWORD', 'changeme')
    )

def add_printer_to_cups(name, uri, location=''):
    """Add a printer to CUPS using lpadmin with authentication."""
    try:
        # Get CUPS admin credentials
        cups_user, cups_pass = get_cups_admin_credentials()

        # Set up environment with CUPS credentials
        env = os.environ.copy()

        # For IPP/IPPS printers, use IPP Everywhere (driverless)
        # The 'everywhere' driver works for most modern network printers

        # Build base command with authentication
        # Using -U for username, password via CUPS_PASSWORD env var
        cmd = ['lpadmin', '-U', cups_user, '-p', name, '-v', uri, '-E']
        env['CUPS_PASSWORD'] = cups_pass

        # Add location if provided
        if location:
            cmd.extend(['-L', location])

        # Try IPP Everywhere first (best for modern printers)
        cmd_everywhere = cmd + ['-m', 'everywhere']
        logger.info(f"Trying to add printer with IPP Everywhere: lpadmin -U {cups_user} -p {name} -v {uri} -E -m everywhere")
        result = subprocess.run(cmd_everywhere, capture_output=True, text=True, timeout=30, env=env)

        if result.returncode == 0:
            # Enable and accept jobs
            subprocess.run(['cupsenable', name], capture_output=True, timeout=10, env=env)
            subprocess.run(['cupsaccept', name], capture_output=True, timeout=10, env=env)
            logger.info(f"Successfully added printer {name} with IPP Everywhere")
            return {'success': True}

        logger.warning(f"IPP Everywhere failed: {result.stderr}")

        # Fallback: Try raw queue (works for any printer but no filtering)
        cmd_raw = cmd + ['-m', 'raw']
        logger.info(f"Trying raw driver: lpadmin -U {cups_user} -p {name} -v {uri} -E -m raw")
        result = subprocess.run(cmd_raw, capture_output=True, text=True, timeout=30, env=env)

        if result.returncode == 0:
            subprocess.run(['cupsenable', name], capture_output=True, timeout=10, env=env)
            subprocess.run(['cupsaccept', name], capture_output=True, timeout=10, env=env)
            logger.info(f"Successfully added printer {name} with raw driver")
            return {'success': True}

        logger.warning(f"Raw driver failed: {result.stderr}")

        # Last resort: Try without specifying a driver (let CUPS auto-detect)
        logger.info(f"Trying without driver specification")
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30, env=env)

        if result.returncode == 0:
            subprocess.run(['cupsenable', name], capture_output=True, timeout=10, env=env)
            subprocess.run(['cupsaccept', name], capture_output=True, timeout=10, env=env)
            logger.info(f"Successfully added printer {name} with auto-detection")
            return {'success': True}

        return {'success': False, 'error': result.stderr or 'Failed to add printer'}

    except subprocess.TimeoutExpired:
        return {'success': False, 'error': 'Operation timed out'}
    except Exception as e:
        return {'success': False, 'error': str(e)}

def remove_printer_from_cups(name):
    """Remove a printer from CUPS using lpadmin."""
    try:
        result = subprocess.run(
            ['lpadmin', '-x', name],
            capture_output=True,
            text=True,
            timeout=10
        )

        if result.returncode == 0:
            return {'success': True}
        else:
            return {'success': False, 'error': result.stderr or 'Failed to remove printer'}

    except subprocess.TimeoutExpired:
        return {'success': False, 'error': 'Operation timed out'}
    except Exception as e:
        return {'success': False, 'error': str(e)}

def send_test_page(printer_name):
    """Send a test page to a printer."""
    try:
        # Create a simple test page
        test_content = """
========================================
          RELAYPRINT TEST PAGE
========================================

Printer: {printer}
Date: {date}

If you can read this, your printer is
configured correctly and ready to use
with the RelayPrint mobile app.

========================================
        """.format(
            printer=printer_name,
            date=datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        )

        # Save test page
        test_file = os.path.join(UPLOAD_FOLDER, 'test_page.txt')
        with open(test_file, 'w') as f:
            f.write(test_content)

        # Print using lp command
        result = subprocess.run(
            ['lp', '-d', printer_name, test_file],
            capture_output=True,
            text=True,
            timeout=10
        )

        # Clean up
        os.unlink(test_file)

        if result.returncode == 0:
            # Extract job ID from output
            job_id = None
            if 'request id is' in result.stdout:
                parts = result.stdout.split()
                for i, p in enumerate(parts):
                    if p == 'is':
                        job_id = parts[i + 1].rstrip(')')
                        break
            return {'success': True, 'job_id': job_id}
        else:
            return {'success': False, 'error': result.stderr or 'Failed to print test page'}

    except subprocess.TimeoutExpired:
        return {'success': False, 'error': 'Operation timed out'}
    except Exception as e:
        return {'success': False, 'error': str(e)}

# ============================================================================
# Error Handlers
# ============================================================================

@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Not found'}), 404

@app.errorhandler(500)
def server_error(error):
    return jsonify({'error': 'Internal server error'}), 500

if __name__ == '__main__':
    # Port 7779 - accessed via Home Assistant Ingress proxy
    app.run(host='0.0.0.0', port=7779, threaded=True)
