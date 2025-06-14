# Developer Documentation

This document provides instructions for setting up a development environment and testing the Home Assistant Printer Relay add-on.

## Table of Contents
- [Development Environment Setup](#development-environment-setup)
- [Repository Structure](#repository-structure)
- [Testing Methods](#testing-methods)
  - [Local Testing](#local-testing)
  - [Integration Testing](#integration-testing)
- [Hosting Options](#hosting-options)
  - [GitHub Repository](#github-repository)
  - [Local HTTPS Server](#local-https-server)

## Development Environment Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/samuelmukoti/ha-printer-relay.git
   cd ha-printer-relay
   ```

2. Set up the development environment:
   ```bash
   ./scripts/setup_dev.sh
   ```
   This script will:
   - Create necessary directories
   - Copy add-on files to the development location
   - Set up the basic structure for testing

## Repository Structure

```
ha-printer-relay/
├── config.yaml           # Add-on configuration
├── Dockerfile           # Container build instructions
├── repository.yaml      # Repository configuration
├── README.md           # Main documentation
├── run.sh              # Add-on startup script
├── rootfs/             # Add-on file system
├── docs/               # Documentation
├── tests/              # Test files
│   ├── integration/    # Integration tests
│   └── test_data/     # Test data
└── scripts/            # Development scripts
    ├── setup_dev.sh    # Development environment setup
    └── serve_local.sh  # Local HTTPS server
```

## Testing Methods

### Local Testing

1. Run the setup script:
   ```bash
   ./scripts/setup_dev.sh
   ```

2. Add the local repository to Home Assistant:
   - Go to Settings > Add-ons > Add-on Store
   - Click ⋮ > Repositories
   - Add: `~/ha-addon-dev/addons/printer-relay`

3. Install and test the add-on:
   - Find "Printer Relay" in the add-on store
   - Install and configure
   - Test functionality

### Integration Testing

Run the integration test suite:
```bash
./tests/run_integration_tests.sh
```

This will:
- Set up a test environment with Docker
- Run integration tests
- Clean up test containers and networks

## Hosting Options

### GitHub Repository

1. Push your repository to GitHub:
   ```bash
   git push origin main
   ```

2. Add to Home Assistant:
   - Go to Settings > Add-ons > Add-on Store
   - Click ⋮ > Repositories
   - Add: `https://github.com/samuelmukoti/ha-printer-relay`

Benefits:
- No additional setup required
- Works with any Home Assistant instance
- Automatic updates
- Better for distribution

### Local HTTPS Server

1. Run the local server script:
   ```bash
   ./scripts/serve_local.sh
   ```

2. The script will:
   - Install mkcert (if needed)
   - Generate SSL certificates
   - Set up Python HTTPS server
   - Copy add-on files

3. Access Options:
   - Local machine: `https://localhost:8099`
   - Remote machine: `https://<your-ip>:8099`

Requirements:
- Python 3
- mkcert tool
- Local network access

Benefits:
- Rapid development
- Quick testing
- No GitHub pushes needed

## Development Workflow

1. Make changes to the code
2. Test locally using either method above
3. Run integration tests
4. Push to GitHub when ready
5. GitHub Actions will run automated tests

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## Troubleshooting

### Common Issues

1. Certificate Issues:
   ```bash
   # Regenerate certificates
   cd ~/ha-addon-dev/certs
   mkcert -install
   mkcert localhost 127.0.0.1
   ```

2. Permission Issues:
   ```bash
   # Fix script permissions
   chmod +x scripts/*.sh
   ```

3. Docker Issues:
   ```bash
   # Clean up test environment
   docker rm -f printer-relay-test cups-pdf-test
   docker network rm printer-test-net
   ```

### Debug Mode

Add to `config.yaml`:
```yaml
options:
  debug: true
```

This will enable verbose logging in the add-on. 