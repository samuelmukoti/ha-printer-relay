# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
