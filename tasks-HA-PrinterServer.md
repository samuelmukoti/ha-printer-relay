## Relevant Files

- `Dockerfile` - Multi-arch container definition using official Home Assistant base images
- `build.yaml` - Build configuration for all supported architectures (aarch64, amd64, armhf, armv7, i386)
- `config.json` - Add-on configuration and schema with port mappings
- `config.yaml` - User-friendly configuration UI definition
- `translations/` - UI localization files
  - `translations/en.yaml` - English translations for the configuration UI
- `rootfs/` - Root filesystem overlay
  - `rootfs/etc/cont-init.d/` - Initialization scripts
    - `01-persistent-storage.sh` - Sets up persistent storage
    - `02-cups-config.sh` - Configures CUPS with secure defaults
  - `rootfs/etc/services.d/` - Service definitions
    - `cups/run` - CUPS service script
    - `avahi/run` - Avahi service script
  - `rootfs/etc/avahi/` - Avahi configuration
    - `avahi-daemon.conf` - Avahi daemon configuration
  - `rootfs/etc/nginx/` - Nginx configuration
    - `nginx.conf` - Nginx configuration for CUPS web interface ingress
  - `rootfs/etc/cups/` - CUPS configuration
    - `cupsd.conf` - CUPS server configuration with ingress support
  - `rootfs/usr/local/bin/` - Custom scripts
    - `printer_discovery.py` - Printer discovery and management script
    - `job_queue_manager.py` - Print job queue management and monitoring
- `tests/` - Test files
  - `test_addon.sh` - Automated test script
  - `test_data/` - Test data and configurations
    - `cupsd-test.conf` - CUPS test configuration
    - `test_print.pdf` - Test PDF file for print tests
    - `avahi-test.conf` - Avahi test configuration
  - `MANUAL_TESTS.md` - Manual testing checklist
  - `test_config.py` - Unit tests for configuration validation
  - `test_printer_discovery.py` - Unit tests for printer discovery
  - `test_job_queue.py` - Unit tests for job queue management
  - `test_cups_integration.py` - Integration tests for CUPS functionality
  - `test_avahi_integration.py` - Integration tests for Avahi advertisement
  - `requirements.txt` - Test dependencies
  - `run_tests.sh` - Test runner script
  - `Dockerfile.test` - Docker configuration for testing
- `docs/` - Documentation files
  - `printer-setup.md` - Comprehensive printer setup guide
- `run.sh` - Main entry point script
- `apparmor.txt` - AppArmor profile for secure port and device access
- `rootfs/` - Directory containing service configurations
  - `rootfs/etc/cups/` - CUPS configuration files
  - `rootfs/etc/avahi/services/` - Avahi service definitions
- `repository.yaml` - Repository information
- `README.md` - Add-on documentation
- `CONTRIBUTING.md` - Contribution guidelines
- `LICENSE` - Project license
- `addon/` - Directory containing the Home Assistant add-on code
  - `Dockerfile` - Container definition for the RelayPrint add-on
  - `run.sh` - Add-on startup script
  - `cups-config/` - CUPS configuration files
  - `avahi-config/` - Avahi configuration files
- `api/` - Directory containing the REST API implementation
  - `routes/print.ts` - Print job submission and status endpoints
  - `routes/print.test.ts` - Unit tests for print routes
  - `middleware/auth.ts` - Authentication middleware
  - `middleware/auth.test.ts` - Unit tests for auth middleware
- `mobile/` - Directory containing mobile app code
  - `ios/` - iOS app implementation
  - `android/` - Android app implementation
  - `shared/` - Shared code between mobile platforms
- `wordpress/` - WordPress plugin directory
  - `relay-print/` - Plugin files
  - `relay-print/tests/` - Plugin unit tests

### Notes

- Unit tests should be written for all API endpoints and critical business logic
- Integration tests should verify end-to-end printing workflows
- Mobile apps should have platform-specific UI tests
- Use Jest for JavaScript/TypeScript testing
- Use XCTest for iOS app testing
- Use JUnit for Android app testing
- Use PHPUnit for WordPress plugin testing

## Tasks

- [x] 1.0 Set up Home Assistant Add-on Infrastructure
  - [x] 1.1 Create basic add-on structure with config.yaml and Dockerfile
  - [x] 1.2 Set up Home Assistant official multi-arch base images with required dependencies
  - [x] 1.3 Configure add-on ports (631 for CUPS, 5353 for mDNS) and security profiles
  - [x] 1.4 Implement add-on configuration UI schema with translations
  - [x] 1.5 Create persistent storage for CUPS configuration with secure defaults
  - [x] 1.6 Write startup script (run.sh) with proper service initialization
  - [x] 1.7 Test add-on installation and basic functionality
  - [x] 1.8 Write unit tests for configuration validation
  - [x] 1.9 Document add-on installation and configuration process

- [x] 2.0 Implement CUPS and Avahi Integration
  - [x] 2.1 Configure CUPS server with proper security settings
  - [x] 2.2 Set up Avahi daemon for printer advertisement
  - [x] 2.3 Configure AirPrint compatibility (DNS-SD records)
  - [x] 2.4 Implement printer discovery mechanism
  - [x] 2.5 Create printer management interface
  - [x] 2.6 Set up job queue management
  - [x] 2.7 Configure CUPS web interface for Home Assistant ingress
  - [x] 2.8 Write integration tests for CUPS functionality
  - [x] 2.9 Write integration tests for Avahi advertisement
  - [x] 2.10 Document printer setup process

- [x] 3.0 Develop REST API for Print Management
  - [x] 3.1 Design API endpoints and data structures
  - [x] 3.2 Implement /api/print POST endpoint
  - [x] 3.3 Implement /api/print/:job_id/status GET endpoint
  - [x] 3.4 Create authentication middleware
  - [x] 3.5 Implement job queue management logic
  - [x] 3.6 Set up error handling and logging
  - [x] 3.7 Write unit tests for API endpoints
  - [x] 3.8 Write integration tests for print workflow
  - [x] 3.9 Document API endpoints and usage
  - [x] 3.10 Implement rate limiting and security measures

- [ ] 4.0 Create iOS Companion App
  - [ ] 4.1 Set up basic iOS project structure
  - [ ] 4.2 Implement AirPrint printer registration
  - [ ] 4.3 Create PDF job capture mechanism
  - [ ] 4.4 Implement API client for job submission
  - [ ] 4.5 Create job status monitoring UI
  - [ ] 4.6 Implement error handling and retry logic
  - [ ] 4.7 Add local settings management
  - [ ] 4.8 Write unit tests for core functionality
  - [ ] 4.9 Write UI tests for critical flows
  - [ ] 4.10 Create App Store listing and documentation

- [ ] 5.0 Create Android Companion App
  - [ ] 5.1 Set up basic Android project structure
  - [ ] 5.2 Implement PrintService interface
  - [ ] 5.3 Create print job capture mechanism
  - [ ] 5.4 Implement API client for job submission
  - [ ] 5.5 Create job status monitoring UI
  - [ ] 5.6 Implement error handling and retry logic
  - [ ] 5.7 Add local settings management
  - [ ] 5.8 Write unit tests for core functionality
  - [ ] 5.9 Write UI tests for critical flows
  - [ ] 5.10 Create Play Store listing and documentation

- [ ] 6.0 Develop WordPress Plugin
  - [ ] 6.1 Create basic plugin structure
  - [ ] 6.2 Implement settings page for printer configuration
  - [ ] 6.3 Create WooCommerce integration
  - [ ] 6.4 Implement automatic print trigger on order status change
  - [ ] 6.5 Add manual print button to order admin page
  - [ ] 6.6 Create template system for print layouts
  - [ ] 6.7 Implement error handling and logging
  - [ ] 6.8 Write unit tests for plugin functionality
  - [ ] 6.9 Write integration tests with WooCommerce
  - [ ] 6.10 Create plugin documentation

- [ ] 7.0 Implement Security Features
  - [ ] 7.1 Set up TLS for all endpoints
  - [ ] 7.2 Implement token-based authentication
  - [ ] 7.3 Create API key management system
  - [ ] 7.4 Set up printer access control
  - [ ] 7.5 Implement audit logging
  - [ ] 7.6 Configure secure storage for credentials
  - [ ] 7.7 Set up CORS and CSP headers
  - [ ] 7.8 Write security-focused unit tests
  - [ ] 7.9 Perform security audit
  - [ ] 7.10 Document security features and best practices

- [ ] 8.0 Create Comprehensive Test Suite
  - [ ] 8.1 Set up CI/CD pipeline
  - [ ] 8.2 Create end-to-end test framework
  - [ ] 8.3 Write integration tests for print workflows
  - [ ] 8.4 Implement load testing scenarios
  - [ ] 8.5 Create automated security tests
  - [ ] 8.6 Set up test coverage reporting
  - [ ] 8.7 Write performance benchmarks
  - [ ] 8.8 Create test documentation
  - [ ] 8.9 Implement automated UI testing
  - [ ] 8.10 Set up continuous monitoring 