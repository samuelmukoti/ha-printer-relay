#!/usr/bin/env python3
"""
RelayPrint REST API

This Flask API is accessed via Home Assistant's Ingress proxy, which handles:
- Authentication (HA user sessions or long-lived access tokens)
- HTTPS/SSL termination
- Access control

For external/mobile app access, use HA's long-lived access tokens.
"""
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from werkzeug.utils import secure_filename
import os
import logging
from datetime import datetime, timezone
from job_queue_manager import queue_manager
from printer_discovery import get_printers

app = Flask(__name__)
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

@app.route('/api/printers', methods=['GET'])
def list_printers():
    """List all available printers."""
    try:
        printers = get_printers()
        return jsonify({'printers': printers})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/queue/status', methods=['GET'])
def get_queue_status():
    """Get overall queue status."""
    try:
        status = queue_manager.get_queue_status()
        return jsonify(status)
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/health', methods=['GET'])
def health_check():
    """API health check endpoint."""
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now(timezone.utc).isoformat(),
        'version': '0.1.1'
    })

# Error handlers
@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Not found'}), 404

@app.errorhandler(500)
def server_error(error):
    return jsonify({'error': 'Internal server error'}), 500

if __name__ == '__main__':
    # Port 7779 - accessed via Home Assistant Ingress proxy
    app.run(host='0.0.0.0', port=7779, threaded=True)
