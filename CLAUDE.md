# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Home Assistant Printer Relay (RelayPrint) - A Home Assistant add-on that replaces Google Cloud Print by providing local and remote printing capabilities through CUPS and Avahi. Supports multi-architecture (aarch64, amd64, armhf, armv7, i386).

## Development Commands

### Running Tests
```bash
# Activate virtual environment
source .venv/bin/activate

# Run unit tests with coverage
python -m pytest tests/ -v --ignore=tests/test_avahi_integration.py --ignore=tests/test_cups_integration.py

# Run a single test file
python -m pytest tests/test_print_api.py -v

# Run a specific test
python -m pytest tests/test_print_api.py::test_health_check -v
```

### Development Setup
```bash
# Set up virtual environment
python3 -m venv .venv
source .venv/bin/activate
pip install -r tests/requirements.txt

# Build Docker image locally
docker build -t printer-relay .
```

### Code Style
- Python: PEP 8, 4-space indentation, 88-char line length (black formatter)

## Architecture

### Service Orchestration (`run.sh`)
The add-on starts services in order:
1. **D-Bus daemon** - Required for Avahi
2. **CUPS daemon** (`cupsd -f`) - Print system backend on port 631
3. **Avahi daemon** - mDNS/DNS-SD for printer discovery
4. **Flask API server** (`print_api.py`) - REST API on port 7779

### Authentication Strategy
**Home Assistant Ingress handles all authentication** - the add-on trusts requests coming through Ingress since HA has already authenticated the user. For mobile app access, use HA's long-lived access tokens.

The only credentials in the add-on are for CUPS administration:
- `cups_admin_user` / `cups_admin_password` - For managing printers via CUPS web UI

### Core Python Components (in `rootfs/usr/local/bin/`)

| File | Purpose |
|------|---------|
| `print_api.py` | Flask REST API - print job submission, printer listing, job management |
| `job_queue_manager.py` | Print job lifecycle management via CUPS |
| `printer_discovery.py` | Printer discovery using pycups library |
| `printer_manager.py` | Web UI server for printer management |

### API Endpoints
All endpoints are protected by Home Assistant Ingress authentication.

- `POST /api/print` - Submit print job (multipart file upload)
- `GET /api/printers` - List available printers
- `GET /api/print/<job_id>/status` - Job status
- `DELETE /api/print/<job_id>` - Cancel job
- `GET /api/queue/status` - Overall queue status
- `GET /api/health` - Health check

### Configuration Files
- `config.yaml` - Add-on metadata matching config.json (YAML format for HA)
- `config.json` - Add-on options schema (ports, CUPS settings, printer defaults)

### Initialization Scripts (`rootfs/etc/cont-init.d/`)
| Script | Purpose |
|--------|---------|
| `01-persistent-storage.sh` | Creates directories, symlinks for CUPS persistence |
| `02-cups-config.sh` | Generates CUPS config from add-on options |

### File System Layout
```
rootfs/
├── usr/local/bin/     # Python services
├── etc/cups/          # CUPS configuration
├── etc/avahi/         # Avahi/mDNS configuration
└── etc/cont-init.d/   # Container initialization scripts
```

## Testing Approach

Tests are in `tests/` directory. Key test files:
- `test_print_api.py` - API endpoint tests with Flask test client
- `test_job_queue.py` - Job queue manager tests (mocked CUPS)
- `test_printer_discovery.py` - Printer discovery tests
- `test_config.py` - Configuration validation tests
- `integration/` - End-to-end Docker-based tests

Note: `test_avahi_integration.py` and `test_cups_integration.py` require Linux-specific modules (dbus, pycups) and are skipped on macOS.

CUPS operations require mocking in unit tests since pycups requires a running CUPS daemon.

## CI/CD Pipeline

GitHub Actions (`.github/workflows/test.yml`):
1. Run pytest with coverage → upload to Codecov
2. Run integration tests in Docker
3. Build multi-architecture Docker images
4. Push to GitHub Container Registry (GHCR)
