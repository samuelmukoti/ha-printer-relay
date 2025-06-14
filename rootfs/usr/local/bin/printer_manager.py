#!/usr/bin/env python3
"""
Printer management web interface for RelayPrint.
Provides a REST API and web UI for managing printers.
"""
import os
import json
from flask import Flask, render_template, jsonify, request, send_from_directory
from printer_discovery import PrinterDiscovery

app = Flask(__name__,
           static_folder='/usr/share/relayprint/static',
           template_folder='/usr/share/relayprint/templates')

# Initialize printer discovery
printer_discovery = PrinterDiscovery()

@app.route('/')
def index():
    """Render the main printer management interface."""
    return render_template('index.html')

@app.route('/static/<path:path>')
def send_static(path):
    """Serve static files."""
    return send_from_directory(app.static_folder, path)

@app.route('/api/printers')
def get_printers():
    """Get all printers and their status."""
    try:
        # Get all printers
        all_printers = printer_discovery.get_all_printers()
        printers_list = []
        
        # Get detailed information for each printer
        for name in all_printers:
            printer_info = printer_discovery.get_printer_attributes(name)
            if printer_info:
                # Add active jobs if any
                try:
                    jobs = printer_discovery.conn.getJobs(which_jobs='active', requested_attributes=['job-name', 'job-id', 'job-state'])
                    printer_jobs = [
                        {
                            'id': job_id,
                            'name': job_info['job-name'],
                            'progress': calculate_job_progress(job_info['job-state'])
                        }
                        for job_id, job_info in jobs.items()
                        if job_info.get('job-printer-uri', '').endswith(f'/{name}')
                    ]
                    printer_info['active_jobs'] = printer_jobs
                except Exception as e:
                    app.logger.error(f"Failed to get jobs for printer {name}: {e}")
                    printer_info['active_jobs'] = []
                
                printers_list.append(printer_info)
        
        return jsonify({'printers': printers_list})
    except Exception as e:
        app.logger.error(f"Failed to get printers: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/printer/<name>/test-print', methods=['POST'])
def test_print(name):
    """Send a test page to the specified printer."""
    try:
        # Create a test page file
        test_page = f"""
        RelayPrint Test Page
        ===================
        Printer: {name}
        Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
        
        If you can read this, your printer is working correctly!
        """
        test_file = '/tmp/test_page.txt'
        with open(test_file, 'w') as f:
            f.write(test_page)
        
        # Send to printer
        job_id = printer_discovery.conn.printFile(name, test_file, "RelayPrint Test Page", {})
        os.remove(test_file)
        
        return jsonify({'success': True, 'job_id': job_id})
    except Exception as e:
        app.logger.error(f"Failed to send test print to {name}: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/printer/<name>/manage')
def manage_printer(name):
    """Render the printer management page."""
    try:
        printer = printer_discovery.get_printer_attributes(name)
        if not printer:
            return "Printer not found", 404
        return render_template('manage_printer.html', printer=printer)
    except Exception as e:
        app.logger.error(f"Failed to get printer {name}: {e}")
        return str(e), 500

@app.route('/printer/add')
def add_printer():
    """Render the add printer page."""
    return render_template('add_printer.html')

def calculate_job_progress(job_state):
    """Calculate job progress percentage based on state."""
    # Map CUPS job states to progress percentages
    state_progress = {
        3: 0,    # Pending
        4: 25,   # Held
        5: 50,   # Processing
        6: 75,   # Stopped
        7: 90,   # Canceled
        8: 95,   # Aborted
        9: 100   # Completed
    }
    return state_progress.get(job_state, 0)

if __name__ == '__main__':
    # In production, this will be behind a reverse proxy
    app.run(host='127.0.0.1', port=5000) 