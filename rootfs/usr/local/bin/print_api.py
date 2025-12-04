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
import threading
import re
import signal
import json
from datetime import datetime, timezone
from job_queue_manager import queue_manager
from printer_discovery import get_printers, PrinterDiscovery

# Global tunnel process management
_tunnel_process = None
_tunnel_lock = threading.Lock()

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


def is_tunnel_enabled():
    """Check if tunnel access is enabled in configuration."""
    try:
        # Check for tunnel config file
        tunnel_config_path = '/data/tunnel/tunnel_config.json'
        if os.path.exists(tunnel_config_path):
            with open(tunnel_config_path) as f:
                config = json.load(f)
                return config.get('enabled', False)

        # Fallback: check for running tunnel URL file
        tunnel_url_path = '/data/tunnel/tunnel_url.txt'
        if os.path.exists(tunnel_url_path):
            return True

        return False
    except Exception as e:
        logger.debug(f"Error checking tunnel config: {e}")
        return False


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
    3. Tunnel access is enabled (tunnel URL acts as a secret)

    When tunnel is enabled, all requests are allowed since the tunnel URL
    is only known to users who have access to the HA dashboard.
    """
    @wraps(f)
    def decorated_function(*args, **kwargs):
        # Ingress requests are pre-authenticated by HA
        if is_ingress_request():
            return f(*args, **kwargs)

        # If tunnel is enabled, allow all requests
        # The tunnel URL itself acts as a secret - only people with HA access know it
        if is_tunnel_enabled():
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
        'version': '0.1.22'
    })

TUNNEL_CONFIG_FILE = '/data/tunnel_config.json'
TUNNEL_URL_FILE = '/data/tunnel/tunnel_url.txt'

# Valid tunnel providers
TUNNEL_PROVIDERS = ['localtunnel', 'cloudflare_quick', 'cloudflare_named']

def load_tunnel_config():
    """Load tunnel config from dashboard config file or addon options."""
    config = {
        'enabled': False,
        'provider': 'localtunnel',  # Default to LocalTunnel
        'tunnel_token': '',
        'tunnel_url': ''
    }

    # First check dashboard config file (takes precedence)
    if os.path.exists(TUNNEL_CONFIG_FILE):
        try:
            with open(TUNNEL_CONFIG_FILE, 'r') as f:
                dashboard_config = json.load(f)
                config['enabled'] = dashboard_config.get('enabled', False)
                config['provider'] = dashboard_config.get('provider', 'localtunnel')
                config['tunnel_token'] = dashboard_config.get('tunnel_token', '')
                # Don't load tunnel_url from JSON - prefer the dynamic file
                logger.debug(f"Loaded tunnel config from {TUNNEL_CONFIG_FILE}: enabled={config['enabled']}, provider={config['provider']}")
        except Exception as e:
            logger.warning(f"Failed to read dashboard config: {e}")
    else:
        logger.debug(f"No dashboard config file found at {TUNNEL_CONFIG_FILE}")

    # Fallback to addon options
    if not config['enabled']:
        options_file = '/data/options.json'
        if os.path.exists(options_file):
            try:
                with open(options_file, 'r') as f:
                    options = json.load(f)
                    tunnel = options.get('tunnel', options.get('cloudflare', {}))
                    config['enabled'] = tunnel.get('enabled', False)
                    config['provider'] = tunnel.get('provider', 'localtunnel')
                    config['tunnel_token'] = tunnel.get('tunnel_token', '')
                    # Don't load tunnel_url from options - prefer the dynamic file
            except Exception as e:
                logger.warning(f"Failed to read addon options: {e}")

    # ALWAYS read the dynamically generated URL from tunnel service
    # This is the authoritative source - the tunnel service writes here when URL is available
    if os.path.exists(TUNNEL_URL_FILE):
        try:
            with open(TUNNEL_URL_FILE, 'r') as f:
                dynamic_url = f.read().strip()
                if dynamic_url:
                    config['tunnel_url'] = dynamic_url
                    # If we have a dynamic URL, tunnel is definitely enabled and active
                    config['enabled'] = True
                    logger.debug(f"Read dynamic tunnel URL from {TUNNEL_URL_FILE}: {dynamic_url}")
        except Exception as e:
            logger.warning(f"Failed to read tunnel URL file: {e}")

    return config

# Legacy alias for backwards compatibility
def load_cloudflare_config():
    """Legacy alias for load_tunnel_config."""
    return load_tunnel_config()


@app.route('/api/config/remote', methods=['GET'])
def get_remote_config():
    """Get remote access configuration for mobile apps.

    This endpoint is intentionally NOT protected by @require_auth
    to allow mobile apps to discover the tunnel URL.
    Returns the tunnel URL if configured.
    """
    config = load_tunnel_config()

    # Check if tunnel is actually running (URL file exists)
    tunnel_active = False
    if config['enabled']:
        if config['provider'] in ['localtunnel', 'cloudflare_quick'] and os.path.exists(TUNNEL_URL_FILE):
            tunnel_active = True
        elif config['provider'] == 'cloudflare_named' and config['tunnel_token']:
            tunnel_active = True

    return jsonify({
        'tunnel_enabled': config['enabled'],
        'tunnel_active': tunnel_active,
        'tunnel_url': config['tunnel_url'] if config['tunnel_url'] else None,
        'tunnel_provider': config['provider'],
        'providers': TUNNEL_PROVIDERS,
        'direct_port': 7779,
        'api_version': '0.1.22',
        'version': '0.1.22'  # Server version for mobile app compatibility
    })


@app.route('/api/config/remote', methods=['POST'])
@require_auth
def save_remote_config():
    """Save remote access configuration.

    Supports three providers:
    - LocalTunnel (default): Simple, reliable, no rate limits
    - Cloudflare Quick Tunnel: Zero config, auto-generated URL
    - Cloudflare Named Tunnel: Requires Cloudflare account and token
    """
    try:
        data = request.get_json()

        # Load existing config
        config = load_tunnel_config()

        # Update with new values
        if 'enabled' in data:
            config['enabled'] = bool(data['enabled'])
        if 'provider' in data:
            config['provider'] = data['provider'] if data['provider'] in TUNNEL_PROVIDERS else 'localtunnel'
        if 'tunnel_url' in data:
            config['tunnel_url'] = data['tunnel_url'].strip() if data['tunnel_url'] else ''
        if 'tunnel_token' in data:
            config['tunnel_token'] = data['tunnel_token'].strip() if data['tunnel_token'] else ''

        # Auto-set provider to cloudflare_named if token provided
        if config['tunnel_token'] and config['provider'] not in ['cloudflare_named']:
            config['provider'] = 'cloudflare_named'

        # Save to config file
        with open(TUNNEL_CONFIG_FILE, 'w') as f:
            json.dump(config, f, indent=2)

        # Update the tunnel enabled marker
        tunnel_config_dir = '/data/tunnel'
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
                # Remove token file for other providers
                token_file = os.path.join(tunnel_config_dir, 'tunnel_token')
                if os.path.exists(token_file):
                    os.remove(token_file)
                provider_name = 'LocalTunnel' if config['provider'] == 'localtunnel' else 'Cloudflare Quick Tunnel'
                logger.info(f"{provider_name} enabled")
                message = f'{provider_name} enabled. Restart the addon to generate a public URL.'
        else:
            # Remove enabled marker
            enabled_file = os.path.join(tunnel_config_dir, 'enabled')
            if os.path.exists(enabled_file):
                os.remove(enabled_file)
            # Clear URL file
            if os.path.exists(TUNNEL_URL_FILE):
                os.remove(TUNNEL_URL_FILE)
            logger.info("Remote tunnel disabled")
            message = 'Remote access disabled.'

        return jsonify({
            'success': True,
            'message': message,
            'tunnel_enabled': config['enabled'],
            'tunnel_provider': config['provider'],
            'tunnel_url': config['tunnel_url'],
            'restart_required': True
        })

    except Exception as e:
        logger.error(f"Failed to save config: {e}")
        return jsonify({'error': str(e)}), 500


# ============================================================================
# Tunnel Management Functions
# ============================================================================

def start_tunnel(provider='localtunnel'):
    """Start a tunnel in a background thread."""
    global _tunnel_process

    with _tunnel_lock:
        # Stop existing tunnel if running
        if _tunnel_process and _tunnel_process.poll() is None:
            logger.info("Stopping existing tunnel process...")
            _tunnel_process.terminate()
            try:
                _tunnel_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                _tunnel_process.kill()

        # Clear old URL file
        if os.path.exists(TUNNEL_URL_FILE):
            os.remove(TUNNEL_URL_FILE)

        # Ensure config directory exists
        os.makedirs(os.path.dirname(TUNNEL_URL_FILE), exist_ok=True)

        # Determine command and URL pattern based on provider
        if provider == 'localtunnel':
            cmd = ['lt', '--port', '7779']
            url_pattern = r'https://[a-zA-Z0-9-]*\.loca\.lt'
            provider_name = 'LocalTunnel'
        elif provider == 'cloudflare_quick':
            cmd = ['/usr/local/bin/cloudflared', 'tunnel', '--url', 'http://localhost:7779', '--no-autoupdate']
            url_pattern = r'https://[a-zA-Z0-9-]+\.trycloudflare\.com'
            provider_name = 'Cloudflare Quick Tunnel'
        else:
            logger.error(f"Unsupported provider for instant start: {provider}")
            return False

        logger.info(f"Starting {provider_name}...")

        try:
            # Start tunnel in background
            _tunnel_process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True
            )

            # Start a thread to capture the URL from output
            def capture_url():
                for line in iter(_tunnel_process.stdout.readline, ''):
                    logger.debug(f"tunnel: {line.strip()}")
                    match = re.search(url_pattern, line)
                    if match:
                        url = match.group(0)
                        with open(TUNNEL_URL_FILE, 'w') as f:
                            f.write(url)
                        logger.info(f"Tunnel URL captured: {url}")

                        # Also update config file
                        try:
                            config = load_tunnel_config()
                            config['tunnel_url'] = url
                            with open(TUNNEL_CONFIG_FILE, 'w') as f:
                                json.dump(config, f, indent=2)
                        except Exception as e:
                            logger.warning(f"Failed to update config with URL: {e}")
                        break

            url_thread = threading.Thread(target=capture_url, daemon=True)
            url_thread.start()

            return True
        except Exception as e:
            logger.error(f"Failed to start tunnel: {e}")
            return False


def start_quick_tunnel():
    """Legacy alias for start_tunnel with cloudflare_quick."""
    return start_tunnel('cloudflare_quick')


def stop_tunnel():
    """Stop the running tunnel process."""
    global _tunnel_process

    with _tunnel_lock:
        if _tunnel_process and _tunnel_process.poll() is None:
            logger.info("Stopping tunnel process...")
            _tunnel_process.terminate()
            try:
                _tunnel_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                _tunnel_process.kill()
            _tunnel_process = None

        # Clear URL file
        if os.path.exists(TUNNEL_URL_FILE):
            os.remove(TUNNEL_URL_FILE)

        return True


def is_tunnel_running():
    """Check if the tunnel process is running."""
    global _tunnel_process

    with _tunnel_lock:
        if _tunnel_process and _tunnel_process.poll() is None:
            return True
        # Also check for externally started tunnel processes (by s6-overlay)
        try:
            # Check for cloudflared
            result = subprocess.run(['pgrep', '-f', 'cloudflared'], capture_output=True)
            if result.returncode == 0:
                return True
            # Check for localtunnel (lt)
            result = subprocess.run(['pgrep', '-f', 'lt --port'], capture_output=True)
            if result.returncode == 0:
                return True
            # Check for node localtunnel
            result = subprocess.run(['pgrep', '-f', 'localtunnel'], capture_output=True)
            return result.returncode == 0
        except Exception:
            return False


@app.route('/api/tunnel/start', methods=['POST'])
@require_auth
def api_start_tunnel():
    """Start the tunnel immediately."""
    try:
        data = request.get_json() or {}
        config = load_tunnel_config()

        # Get provider from request or config
        provider = data.get('provider', config.get('provider', 'localtunnel'))

        if provider == 'cloudflare_named':
            return jsonify({
                'success': False,
                'error': 'Named tunnels require addon restart. Use LocalTunnel or Cloudflare Quick Tunnel for instant start.'
            }), 400

        # Update config to enabled
        config['enabled'] = True
        config['provider'] = provider
        with open(TUNNEL_CONFIG_FILE, 'w') as f:
            json.dump(config, f, indent=2)

        # Create enabled marker for s6 service
        tunnel_config_dir = '/data/tunnel'
        os.makedirs(tunnel_config_dir, exist_ok=True)
        with open(os.path.join(tunnel_config_dir, 'enabled'), 'w') as f:
            f.write('1')

        # Start tunnel
        provider_name = 'LocalTunnel' if provider == 'localtunnel' else 'Cloudflare Quick Tunnel'
        if start_tunnel(provider):
            return jsonify({
                'success': True,
                'message': f'{provider_name} starting... URL will appear in a few seconds.',
                'tunnel_starting': True,
                'provider': provider
            })
        else:
            return jsonify({
                'success': False,
                'error': 'Failed to start tunnel process'
            }), 500

    except Exception as e:
        logger.error(f"Failed to start tunnel: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/tunnel/stop', methods=['POST'])
@require_auth
def api_stop_tunnel():
    """Stop the running tunnel."""
    try:
        # Update config
        config = load_tunnel_config()
        config['enabled'] = False
        config['tunnel_url'] = ''
        with open(TUNNEL_CONFIG_FILE, 'w') as f:
            json.dump(config, f, indent=2)

        # Remove enabled marker
        enabled_file = '/data/tunnel/enabled'
        if os.path.exists(enabled_file):
            os.remove(enabled_file)

        # Stop tunnel
        stop_tunnel()

        # Also try to kill any externally started tunnel processes
        try:
            subprocess.run(['pkill', '-f', 'cloudflared'], capture_output=True)
            subprocess.run(['pkill', '-f', 'lt --port'], capture_output=True)
            subprocess.run(['pkill', '-f', 'localtunnel'], capture_output=True)
        except Exception:
            pass

        return jsonify({
            'success': True,
            'message': 'Tunnel stopped.'
        })

    except Exception as e:
        logger.error(f"Failed to stop tunnel: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/tunnel/status', methods=['GET'])
def api_tunnel_status():
    """Get detailed tunnel status."""
    config = load_tunnel_config()
    running = is_tunnel_running()

    # Read URL from file if exists
    tunnel_url = None
    if os.path.exists(TUNNEL_URL_FILE):
        try:
            with open(TUNNEL_URL_FILE, 'r') as f:
                tunnel_url = f.read().strip()
        except Exception:
            pass

    if not tunnel_url:
        tunnel_url = config.get('tunnel_url')

    return jsonify({
        'enabled': config.get('enabled', False),
        'running': running,
        'provider': config.get('provider', 'localtunnel'),
        'providers': TUNNEL_PROVIDERS,
        'url': tunnel_url,
        'has_token': bool(config.get('tunnel_token'))
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
