# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
