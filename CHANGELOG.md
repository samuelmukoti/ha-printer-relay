# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.21f] - 2025-12-03

### Fixed
- **Tunnel init script config keys** - Updated `30-cloudflare-tunnel.sh` to use new `tunnel` config
  - Now reads `tunnel.enabled`, `tunnel.provider`, `tunnel.tunnel_token` instead of `cloudflare.*`
  - Properly stores provider selection for s6 tunnel service to use
  - Synced config.json with config.yaml tunnel schema

## [0.1.21e] - 2025-12-03

### Added
- **LocalTunnel support** - Added LocalTunnel as an alternative tunnel provider
  - LocalTunnel is now the default provider (simpler, no rate limits)
  - Choose between LocalTunnel, Cloudflare Quick Tunnel, or Cloudflare Named Tunnel
  - Provider selection in dashboard Settings tab
- **Multi-provider tunnel service** - Unified tunnel service supporting all providers
  - Automatic fallback to LocalTunnel if unknown provider configured
  - Provider-aware URL pattern matching for each tunnel type

### Changed
- Renamed service directory from `cloudflared` to `tunnel` for provider-agnostic naming
- Updated API endpoints to support provider selection
- Dashboard UI now shows provider dropdown when remote access is enabled
- Config schema updated: `cloudflare` section renamed to `tunnel` with `provider` option

## [0.1.21d] - 2025-12-03

### Fixed
- **Cloudflared crash loop** - Fixed rapid restart loop when Quick Tunnel fails to connect
  - Added 30-second backoff delay between restart attempts
  - Improved output capture using tee instead of pipe-to-while-read
  - Background URL capture thread with log file parsing
- **Tunnel URL capture** - Fixed URL not being captured from cloudflared output

## [0.1.21c] - 2025-12-03

### Added
- **Instant Tunnel Start/Stop** - Remote access can now be enabled/disabled without restarting the addon
  - New `/api/tunnel/start` endpoint to start Quick Tunnel immediately
  - New `/api/tunnel/stop` endpoint to stop tunnel
  - New `/api/tunnel/status` endpoint for real-time status
- **URL Polling** - Dashboard automatically polls for tunnel URL after starting
- **Copy URL Button** - Click to copy tunnel URL to clipboard in Settings

### Fixed
- **Settings UI display issues** - Fixed missing CSS variable that caused broken URL display box
- **Missing copyTunnelUrl function** - Added missing JavaScript function for URL copy button

## [0.1.21b] - 2025-12-03

### Added
- **Quick Tunnel Mode** - Zero-config remote access using Cloudflare's free Quick Tunnels
  - One-click enable from dashboard - no Cloudflare account required
  - Auto-generates public URL (e.g., `https://random-words.trycloudflare.com`)
  - URL automatically displayed in dashboard and available to mobile app
- **Dual Tunnel Modes** - Choose between Quick Tunnel (simple) or Named Tunnel (persistent)
  - Quick Tunnel: Instant setup, URL changes on restart
  - Named Tunnel: Requires Cloudflare account, persistent custom domain

### Changed
- **Improved tunnel service** - cloudflared run script now supports both modes
- **Mobile app API updates** - Added `tunnel_mode` and `tunnel_active` fields to remote config API
- **Dashboard UI** - Simplified remote access settings with mode selection

## [0.1.21a] - 2025-12-03

### Fixed
- **Cloudflare Tunnel configuration persistence** - Configuration now saved to dedicated config file
- **Dashboard tunnel settings** - Added UI for configuring tunnel token and URL from dashboard
- **POST /api/config/remote endpoint** - Save tunnel configuration from dashboard UI

## [0.1.21] - 2025-12-03

### Added
- **Remote Access Status Dashboard** - New status card on dashboard showing Cloudflare Tunnel connection status
  - Real-time tunnel connectivity indicator (connected/disconnected/loading)
  - Click to view tunnel URL and copy to clipboard
  - Visual status indicators with color coding
- **Remote Configuration API** - New `/api/config/remote` endpoint for mobile apps to discover tunnel URL
- **Cloudflare Tunnel Configuration** - Added `cloudflare.enabled`, `cloudflare.tunnel_token`, and `cloudflare.tunnel_url` options

### Changed
- Updated dashboard UI with improved styling and remote access status indicators
- Added tunnel status checking on page load with auto-refresh

## [0.1.20] - 2025-12-03

### Added
- **Mobile App OAuth2 Authentication** - Android app now uses OAuth2 with PKCE flow for secure authentication
- **Bearer Token Authentication** - API endpoints now accept Bearer token authentication for mobile access
- **Port 7779 Exposure** - Added explicit port mapping for mobile app direct access

### Changed
- Improved mobile app setup flow with WebView-based OAuth login
- Enhanced addon discovery to support both local and remote access patterns

## [0.1.19] - 2025-12-03

### Fixed
- **Fixed lpadmin authentication errors** - Completely rewrote CUPS configuration:
  - Simplified cupsd.conf to be fully permissive (container is protected by HA Ingress)
  - Removed all authentication requirements for local operations
  - `<Limit All>` now uses `Allow all` with no auth required
  - Removed deprecated raw driver fallback from lpadmin attempts
  - This is secure because the container is isolated and all access goes through HA authentication

## [0.1.18] - 2025-12-03

### Fixed
- **Fixed lpadmin "Forbidden" error** - Multiple fixes to resolve CUPS authentication issues:
  - Added `SystemGroup lpadmin root sys` to cupsd.conf
  - Changed printer admin policy to `AuthType None` with `Allow all`
  - Create CUPS admin user at startup and add to lpadmin/lp/sys groups
  - Add root user to lpadmin group for command-line operations
  - lpadmin now uses configured CUPS credentials via `-U` flag and `CUPS_PASSWORD` env var

## [0.1.17] - 2025-12-03

### Added
- **Expandable printer cards in discovery UI** - Click on a discovered printer to see detailed information before adding
- **Protocol selection** - When a printer supports multiple protocols (IPP, IPPS/AirPrint), users can now choose which one to use
- **Printer deduplication** - Same printer discovered via multiple protocols is now grouped into a single card with protocol options

### Changed
- Improved printer discovery API to return grouped printers with all available protocols
- Secure protocols (IPPS) are now highlighted and recommended when available
- UI shows IP address, hostname, port, and URI for each protocol option

## [0.1.16] - 2025-12-03

### Fixed
- **Fixed garbled printer names** - Added `decode_mdns_name()` function to decode mDNS octal escape sequences (e.g., `\032` -> space). Printer names now display correctly as "HP LaserJet M110w" instead of "HP\032LaserJet\032M110w"
- **Fixed "Forbidden" error when adding printers** - Updated CUPS policy to allow printer administration from localhost without authentication. The API runs locally inside the container and is already protected by HA Ingress authentication

### Changed
- Applied mDNS name decoding in both printer discovery and printer add endpoints

## [0.1.15] - 2025-12-03

### Fixed
- **Fixed lpadmin driver argument error** - Rewrote `add_printer_to_cups()` with proper driver fallback logic
  - Try IPP Everywhere (`-m everywhere`) first for modern network printers
  - Fall back to raw queue (`-m raw`) if IPP Everywhere fails
  - Final fallback without driver specification for auto-detection
  - Fixes "Unknown argument driverless:..." error

## [0.1.14] - 2025-12-03

### Fixed
- **Fixed dashboard 404 errors** - Changed absolute paths to relative paths for HA Ingress compatibility
  - Static assets: `/static/styles.css` -> `static/styles.css`
  - API calls: `/api/...` -> `api/...`
  - HA Ingress proxy requires relative paths to work correctly

## [0.1.13] - 2025-12-03

### Fixed
- **Fixed s6-overlay v3 /init permission denied** - Multiple fixes to resolve container startup
  - Added `chmod +x /init` in Dockerfile
  - Set git executable permissions on run scripts
  - Added proper `user` and `type` bundle files for s6-rc services
  - Uses single `relayprint` service that manages D-Bus, Avahi, CUPS, and API startup

### Changed
- Synced config.json with config.yaml (version, apparmor, stdin_open settings)

## [0.1.11] - 2025-12-03

### Changed
- **Disabled AppArmor** - Set `apparmor: false` to resolve profile loading issues on some HA installations
- Container security is maintained through Docker isolation and HA Ingress authentication

## [0.1.10] - 2025-12-02

### Added
- **Added `stdin: true`** - Missing config option from EverythingSmartHome addon pattern

### Changed
- **Reverted base image to 3.22** - Using latest base image as requested

## [0.1.9] - 2025-12-02

### Changed
- **Aligned with official HA addon patterns** - Based on home-assistant/addons/configurator
  - Changed shebang from `#!/usr/bin/with-contenv bashio` to `#!/command/with-contenv bashio`
  - Downgraded base image from 3.22 to 3.19 (same as official addons)
  - Removed default BUILD_FROM value in Dockerfile (let build.yaml control it)
  - Simplified Dockerfile to match official pattern

## [0.1.8] - 2025-12-02

### Fixed
- **Explicit /init permission fix** - Added `chmod +x /init` to Dockerfile
  - Ensures s6-overlay init script has execute permissions after image build
  - Also adds find command to chmod all run/finish scripts in s6-overlay

## [0.1.7] - 2025-12-02

### Changed
- **Complete restructure to match working HA add-on pattern** - Based on EverythingSmartHome add-on
  - Simplified to single `relayprint` service instead of multiple separate services
  - Removed cont-init.d scripts, all initialization now in single run script
  - Updated base images from 3.20 to 3.22 (matching working add-on)
  - Simplified Dockerfile to match proven working pattern
  - Run script now handles: D-Bus, Avahi, CUPS, and API startup in sequence

### Fixed
- **Container startup permission issues** - Restructured to eliminate `/init: Permission denied` error
  - Single service architecture avoids s6-overlay complexity issues
  - Proper service startup sequence in single run script

## [0.1.6] - 2025-12-03

### Fixed
- **Update build.yaml to use base image 3.20** - build.yaml was using 3.18 while Dockerfile used 3.20
  - Home Assistant uses build.yaml for the actual build, not the Dockerfile ARG default
  - Updated all architectures to use consistent 3.20 base images
- **Fixed image source URL** in build.yaml labels

## [0.1.5] - 2025-12-03

### Fixed
- **Fix /init permission denied error** - Added `chmod +x /init` to Dockerfile
  - The s6-overlay init script from the base image needs execute permissions
  - This was causing the add-on to fail to start on Home Assistant

## [0.1.4] - 2025-12-03

### Fixed
- **Container startup fix** - Migrated to s6-overlay v3 service structure
  - Uses `/etc/s6-overlay/s6-rc.d/` instead of deprecated `/etc/services.d/`
  - Proper service dependencies (D-Bus → CUPS/Avahi → API)
  - Fixed "/init: Permission denied" startup error

### Changed
- Updated Dockerfile to match Home Assistant add-on best practices
- Updated base image reference to use newer Alpine version
- Simplified Dockerfile by removing redundant EXPOSE/VOLUME/LABEL directives
- Added `--break-system-packages` flag for pip install compatibility

## [0.1.3] - 2025-12-02

### Added
- **Web Dashboard UI** - Beautiful, user-friendly dashboard accessible via HA sidebar
  - Home Assistant inspired dark theme design
  - Dashboard view with printer status and queue summary
  - Printers tab to manage connected printers
  - Discover tab to scan and add network printers
  - Print Queue tab to monitor and manage print jobs
- **Network Printer Discovery** - Scan local network for IPP/AirPrint printers
  - Uses Avahi/mDNS to discover printers automatically
  - One-click add discovered printers to RelayPrint
  - Manual printer addition with custom URI
- **Printer Management API**
  - `GET /api/discover` - Discover network printers
  - `POST /api/printers/add` - Add printer to CUPS
  - `DELETE /api/printers/<name>` - Remove printer from CUPS
  - `POST /api/printers/test` - Send test page to printer

### Changed
- Flask API now serves web dashboard at root URL
- Added avahi-tools package for network printer discovery

## [0.1.2] - 2025-12-02

### Fixed
- Docker build now works on all architectures (aarch64, amd64, armhf, armv7, i386)
- Fixed AppArmor profile name to match add-on slug
- Fixed CUPS configuration with proper directive syntax
- Added missing Cancel-Job/Cancel-Jobs policy limits
- Added fallback defaults for configuration values

### Changed
- Removed non-existent cups-pdf package from Alpine 3.18
- Added build dependencies for pycups compilation
- Improved CUPS LogLevel validation
- Updated README with author information and support links

## [0.1.1] - 2025-12-01

### Changed
- Simplified architecture by leveraging Home Assistant Ingress for authentication
- Removed custom JWT authentication layer
- Removed Nginx reverse proxy
- Changed API port to 7779

### Removed
- api_username, api_password, api_secret configuration options

## [0.1.0] - 2025-11-30

### Added
- Initial release
- CUPS print server integration
- Avahi/mDNS printer discovery
- REST API for print job management
- Home Assistant Ingress support
- Multi-architecture Docker builds
- AppArmor security profile
- Persistent storage for CUPS configuration and print jobs
