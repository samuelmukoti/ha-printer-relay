#!/usr/bin/env python3
"""
RelayPrint REST API & Web Dashboard

This Flask API can be accessed via:
1. Home Assistant's Ingress proxy (handles auth via session cookies)
2. Direct API access on port 7779 with Bearer token authentication

For external/mobile app access, use HA OAuth tokens as Bearer tokens.
"""
from flask import Flask, request, jsonify, send_file, render_template, send_from_directory
from flask_cors import CORS
from werkzeug.utils import secure_filename
from functools import wraps
import os
import logging
import subprocess
import socket
import requests
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

# Home Assistant Supervisor API for token validation
SUPERVISOR_TOKEN = os.environ.get('SUPERVISOR_TOKEN', '')
HA_BASE_URL = os.environ.get('HA_BASE_URL', 'http://supervisor/core')

app.config['MAX_CONTENT_LENGTH'] = MAX_CONTENT_LENGTH
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def is_ingress_request():
    """Check if request comes from HA Ingress (already authenticated)."""
    # Ingress requests have specific headers set by HA
    return request.headers.get('X-Ingress-Path') is not None


def validate_ha_token(token):
    """Validate a Home Assistant access token by calling HA API."""
    try:
        # Use the token to call HA's /api/ endpoint
        headers = {'Authorization': f'Bearer {token}'}
        # Try to get HA config - this validates the token
        response = requests.get(
            f'{HA_BASE_URL}/api/',
            headers=headers,
            timeout=5
        )
        return response.status_code == 200
    except Exception as e:
        logger.warning(f"Token validation failed: {e}")
        # If we can't reach HA API, try alternate validation
        # For internal requests via Supervisor, also accept Supervisor token
        if SUPERVISOR_TOKEN and token == SUPERVISOR_TOKEN:
            return True
        return False


def require_auth(f):
    """Decorator to require authentication for API endpoints.

    Allows requests that either:
    1. Come through HA Ingress (X-Ingress-Path header present)
    2. Have a valid Bearer token in Authorization header
    """
    @wraps(f)
    def decorated_function(*args, **kwargs):
        # Ingress requests are pre-authenticated by HA
        if is_ingress_request():
            return f(*args, **kwargs)

        # Check for Bearer token
        auth_header = request.headers.get('Authorization', '')
        if auth_header.startswith('Bearer '):
            token = auth_header[7:]  # Remove 'Bearer ' prefix
            if validate_ha_token(token):
                return f(*args, **kwargs)
            else:
                return jsonify({'error': 'Invalid or expired token'}), 401

        # No valid authentication
        return jsonify({'error': 'Authentication required'}), 401

    return decorated_function

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
@require_auth
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
@require_auth
def get_job_status(job_id):
    """Get status of a print job."""
    try:
        status = queue_manager.get_job_status(job_id)
        return jsonify(status)
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/print/<int:job_id>', methods=['DELETE'])
@require_auth
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
@require_auth
def list_printers():
    """List all available printers configured in CUPS."""
    try:
        printers = get_printers()
        return jsonify({'printers': printers})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/discover', methods=['GET'])
@require_auth
def discover_printers():
    """Discover network printers using mDNS/DNS-SD (Avahi)."""
    try:
        discovered = discover_network_printers()
        return jsonify({'printers': discovered})
    except Exception as e:
        logger.error(f"Error discovering printers: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/printers/add', methods=['POST'])
@require_auth
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
@require_auth
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
@require_auth
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
@require_auth
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
    """API health check endpoint.

    This endpoint is intentionally NOT protected by @require_auth
    to allow mobile apps to probe for the addon without a token.
    """
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now(timezone.utc).isoformat(),
        'version': '0.1.21b'
    })

CLOUDFLARE_CONFIG_FILE = '/data/cloudflare_config.json'
TUNNEL_URL_FILE = '/data/cloudflare/tunnel_url.txt'

def load_cloudflare_config():
    """Load Cloudflare config from dashboard config file or addon options."""
    config = {
        'enabled': False,
        'tunnel_token': '',
        'tunnel_url': '',
        'mode': 'quick'  # 'quick' or 'named'
    }
    
    # First check dashboard config file (takes precedence)
    if os.path.exists(CLOUDFLARE_CONFIG_FILE):
        try:
            import json
            with open(CLOUDFLARE_CONFIG_FILE, 'r') as f:
                dashboard_config = json.load(f)
                config['enabled'] = dashboard_config.get('enabled', False)
                config['tunnel_token'] = dashboard_config.get('tunnel_token', '')
                config['tunnel_url'] = dashboard_config.get('tunnel_url', '')
                config['mode'] = dashboard_config.get('mode', 'quick')
        except Exception as e:
            logger.warning(f"Failed to read dashboard config: {e}")
    
    # Fallback to addon options
    if not config['enabled']:
        options_file = '/data/options.json'
        if os.path.exists(options_file):
            try:
                import json
                with open(options_file, 'r') as f:
                    options = json.load(f)
                    cloudflare = options.get('cloudflare', {})
                    config['enabled'] = cloudflare.get('enabled', False)
                    config['tunnel_token'] = cloudflare.get('tunnel_token', '')
                    config['tunnel_url'] = cloudflare.get('tunnel_url', '')
            except Exception as e:
                logger.warning(f"Failed to read addon options: {e}")
    
    # For Quick Tunnel mode, read the dynamically generated URL
    if config['enabled'] and (not config['tunnel_token'] or config['mode'] == 'quick'):
        if os.path.exists(TUNNEL_URL_FILE):
            try:
                with open(TUNNEL_URL_FILE, 'r') as f:
                    dynamic_url = f.read().strip()
                    if dynamic_url:
                        config['tunnel_url'] = dynamic_url
                        config['mode'] = 'quick'
            except Exception as e:
                logger.warning(f"Failed to read tunnel URL file: {e}")
    
    return config


@app.route('/api/config/remote', methods=['GET'])
def get_remote_config():
    """Get remote access configuration for mobile apps.

    This endpoint is intentionally NOT protected by @require_auth
    to allow mobile apps to discover the tunnel URL.
    Returns the Cloudflare Tunnel URL if configured.
    """
    config = load_cloudflare_config()
    
    # Check if tunnel is actually running (URL file exists for quick tunnel)
    tunnel_active = False
    if config['enabled']:
        if config['mode'] == 'quick' and os.path.exists(TUNNEL_URL_FILE):
            tunnel_active = True
        elif config['mode'] == 'named' and config['tunnel_token']:
            tunnel_active = True

    return jsonify({
        'tunnel_enabled': config['enabled'],
        'tunnel_active': tunnel_active,
        'tunnel_url': config['tunnel_url'] if config['tunnel_url'] else None,
        'tunnel_mode': config['mode'],  # 'quick' or 'named'
        'direct_port': 7779,
        'api_version': '0.1.21b'
    })


@app.route('/api/config/remote', methods=['POST'])
@require_auth
def save_remote_config():
    """Save remote access configuration.
    
    Supports two modes:
    - Quick Tunnel (default): Just enable, auto-generates URL
    - Named Tunnel: Requires Cloudflare account and token
    """
    try:
        data = request.get_json()
        
        # Load existing config
        config = load_cloudflare_config()
        
        # Update with new values
        if 'enabled' in data:
            config['enabled'] = bool(data['enabled'])
        if 'mode' in data:
            config['mode'] = data['mode'] if data['mode'] in ['quick', 'named'] else 'quick'
        if 'tunnel_url' in data:
            config['tunnel_url'] = data['tunnel_url'].strip() if data['tunnel_url'] else ''
        if 'tunnel_token' in data:
            # Update token (can be empty for quick tunnel mode)
            config['tunnel_token'] = data['tunnel_token'].strip() if data['tunnel_token'] else ''
        
        # Auto-detect mode based on token
        if config['tunnel_token']:
            config['mode'] = 'named'
        else:
            config['mode'] = 'quick'
        
        # Save to config file
        import json
        with open(CLOUDFLARE_CONFIG_FILE, 'w') as f:
            json.dump(config, f, indent=2)
        
        # Update the tunnel enabled marker
        tunnel_config_dir = '/data/cloudflare'
        os.makedirs(tunnel_config_dir, exist_ok=True)
        
        if config['enabled']:
            # Create enabled marker
            with open(os.path.join(tunnel_config_dir, 'enabled'), 'w') as f:
                f.write('1')
            
            if config['tunnel_token']:
                # Named tunnel mode - write token
                with open(os.path.join(tunnel_config_dir, 'tunnel_token'), 'w') as f:
                    f.write(config['tunnel_token'])
                os.chmod(os.path.join(tunnel_config_dir, 'tunnel_token'), 0o600)
                logger.info("Cloudflare Named Tunnel configuration saved")
                message = 'Named tunnel configured. Restart the addon to apply changes.'
            else:
                # Quick tunnel mode - remove token file
                token_file = os.path.join(tunnel_config_dir, 'tunnel_token')
                if os.path.exists(token_file):
                    os.remove(token_file)
                logger.info("Cloudflare Quick Tunnel enabled")
                message = 'Quick Tunnel enabled. Restart the addon to generate a public URL.'
        else:
            # Remove enabled marker
            enabled_file = os.path.join(tunnel_config_dir, 'enabled')
            if os.path.exists(enabled_file):
                os.remove(enabled_file)
            # Clear URL file
            if os.path.exists(TUNNEL_URL_FILE):
                os.remove(TUNNEL_URL_FILE)
            logger.info("Cloudflare Tunnel disabled")
            message = 'Remote access disabled.'
        
        return jsonify({
            'success': True,
            'message': message,
            'tunnel_enabled': config['enabled'],
            'tunnel_mode': config['mode'],
            'tunnel_url': config['tunnel_url'],
            'restart_required': True
        })
        
    except Exception as e:
        logger.error(f"Failed to save config: {e}")
        return jsonify({'error': str(e)}), 500

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

def add_printer_to_cups(name, uri, location=''):
    """Add a printer to CUPS using lpadmin.

    CUPS is configured with permissive settings (no auth required) since
    the container is protected by Home Assistant Ingress authentication.
    """
    try:
        # Build base command - no authentication needed with permissive CUPS config
        cmd = ['lpadmin', '-p', name, '-v', uri, '-E']

        # Add location if provided
        if location:
            cmd.extend(['-L', location])

        # Try IPP Everywhere first (best for modern printers)
        cmd_everywhere = cmd + ['-m', 'everywhere']
        logger.info(f"Trying to add printer with IPP Everywhere: {' '.join(cmd_everywhere)}")
        result = subprocess.run(cmd_everywhere, capture_output=True, text=True, timeout=30)

        if result.returncode == 0:
            # Enable and accept jobs
            subprocess.run(['cupsenable', name], capture_output=True, timeout=10)
            subprocess.run(['cupsaccept', name], capture_output=True, timeout=10)
            logger.info(f"Successfully added printer {name} with IPP Everywhere")
            return {'success': True}

        logger.warning(f"IPP Everywhere failed: {result.stderr}")

        # Fallback: Try without specifying a driver (let CUPS auto-detect)
        # Skip raw driver as it's deprecated
        logger.info(f"Trying without driver specification: {' '.join(cmd)}")
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)

        if result.returncode == 0:
            subprocess.run(['cupsenable', name], capture_output=True, timeout=10)
            subprocess.run(['cupsaccept', name], capture_output=True, timeout=10)
            logger.info(f"Successfully added printer {name} with auto-detection")
            return {'success': True}

        logger.warning(f"Auto-detection failed: {result.stderr}")

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
