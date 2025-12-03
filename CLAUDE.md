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

### Service Orchestration (s6-overlay v3)
The add-on uses s6-overlay v3 for service management with proper dependencies:
1. **D-Bus daemon** - Base service, required for Avahi
2. **CUPS daemon** (`cupsd -f`) - Print system backend on port 631, depends on dbus
3. **Avahi daemon** - mDNS/DNS-SD for printer discovery, depends on dbus
4. **Flask API server** (`print_api.py`) - REST API on port 7779, depends on dbus, avahi, and cupsd

Services are defined in `/etc/s6-overlay/s6-rc.d/` with proper dependencies ensuring correct startup order.

### Authentication Strategy

**Important:** HA Ingress does NOT work for REST API calls from mobile apps. Ingress uses session cookies (browser-only) and the Supervisor APIs require admin access that OAuth/LLAT tokens don't have.

**For Browser (Web UI):**
- Home Assistant Ingress handles authentication via session cookies

**For Mobile Apps (REST API):**
- Direct port access (7779) with Bearer token authentication
- Tokens validated against HA's `/api/` endpoint
- Remote access via built-in Cloudflare Tunnel (recommended) or port forwarding

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
| `10-setup-cups.sh` | Creates directories, symlinks for CUPS persistence, generates CUPS config from add-on options |
| `20-app-dirs.sh` | Creates application directories for print jobs and API data |

### File System Layout
```
rootfs/
├── usr/local/bin/           # Python services
├── etc/cups/                # CUPS configuration
├── etc/avahi/               # Avahi/mDNS configuration
├── etc/cont-init.d/         # Container initialization scripts
└── etc/s6-overlay/s6-rc.d/  # s6-overlay v3 service definitions
    ├── dbus/                # D-Bus service
    ├── avahi/               # Avahi service (depends on dbus)
    ├── cupsd/               # CUPS service (depends on dbus)
    ├── relayprint/          # API service (depends on dbus, avahi, cupsd)
    └── user/                # User bundle containing all services
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

## Deployment Workflows

### Deploying the Add-on to Home Assistant

1. **Commit and push changes to GitHub:**
   ```bash
   git add .
   git commit -m "Description of changes"
   git push
   ```

2. **Wait for GitHub Actions to complete** (builds multi-arch Docker images)

3. **Update the add-on on Home Assistant instance:**
   ```bash
   # SSH into HA instance
   ssh root@192.168.86.5

   # Navigate to addon directory and pull latest
   cd /addons/ha-printer-relay
   git pull

   # Restart the addon via HA Supervisor API
   ha addons restart local_relay_print

   # Or use the HA web UI: Settings → Add-ons → RelayPrint → Restart
   ```

4. **Verify deployment:**
   - Check addon logs in HA UI or via `ha addons logs local_relay_print`
   - Test API health endpoint: `curl http://localhost:7779/api/health`

### Deploying the Android App to Connected Device

1. **Navigate to the Android project:**
   ```bash
   cd mobile/android
   ```

2. **Build the debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

3. **Verify device is connected:**
   ```bash
   adb devices
   ```

4. **Install and launch the app:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.harelayprint/.app.MainActivity
   ```

5. **Monitor logs during testing:**
   ```bash
   # Clear logs and watch specific tags
   adb logcat -c
   adb logcat -s "HaAuthManager:*" "ApiClientFactory:*" "SetupViewModel:*"
   ```

### Mobile App Authentication Flow

The mobile app uses OAuth2 with PKCE to authenticate with Home Assistant:
1. User enters HA URL (e.g., `https://lahome.mukoti.com`)
2. App opens WebView with HA OAuth login page
3. User authenticates with HA credentials
4. App captures OAuth callback with authorization code
5. App exchanges code for access token
6. App discovers RelayPrint addon via direct port (7779) or ingress proxy
7. All subsequent API calls use Bearer token authentication

### Add-on API Authentication

The add-on supports two authentication modes:
1. **Ingress (browser/HA sidebar):** Requests with `X-Ingress-Path` header are pre-authenticated by HA
2. **Direct API (mobile app):** Requests with `Authorization: Bearer <token>` header are validated against HA's `/api/` endpoint

The `/api/health` and `/api/config/remote` endpoints are intentionally unprotected to allow discovery probing.

### Cloudflare Tunnel (Remote Access)

The add-on includes built-in Cloudflare Tunnel support for secure remote access without port forwarding:

**Configuration Options (in addon settings):**
- `cloudflare.enabled` - Enable/disable tunnel
- `cloudflare.tunnel_token` - Token from Cloudflare Zero Trust dashboard
- `cloudflare.tunnel_url` - Public URL (e.g., `https://relayprint.yourdomain.com`)

**Service:** The `cloudflared` service runs as an s6-overlay longrun service, depends on `relayprint`.

**Setup Steps:**
1. Create tunnel in Cloudflare Zero Trust dashboard
2. Configure public hostname → `localhost:7779`
3. Copy tunnel token to addon settings
4. Restart addon

See `docs/MOBILE_APP_AUTH.md` for detailed setup guide.
