# Home Assistant Printer Relay Add-on

This add-on enables you to relay print jobs from mobile devices to network printers through your Home Assistant instance.

## Installation

1. Navigate in your Home Assistant frontend to **Settings** -> **Add-ons** -> **Add-on Store**
2. Click the 3-dots menu in the top right -> **Repositories**
3. Add this repository URL:
   ```
   https://github.com/samuelmukoti/ha-printer-relay
   ```
4. The "Printer Relay" add-on should now be available for installation

## Configuration

After installation:

1. Set your `api_secret` in the add-on configuration
2. Configure the `token_expiry` if needed (default: 3600 seconds)
3. Start the add-on

## Mobile App Setup

Configure your mobile app with:
- Home Assistant URL
- The API secret you configured
- The printer relay endpoint (usually `http://your-ha-instance:8080`)

## Development

For development instructions, please see [DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Support

For issues and feature requests, please use the [GitHub issue tracker](https://github.com/samuelmukoti/ha-printer-relay/issues).

# RelayPrint - Home Assistant Printer Server Add-on

A Home Assistant add-on that replaces Google Cloud Print functionality by providing local and remote printing capabilities through CUPS and Avahi.

## Features

- Local printer sharing via AirPrint and IPP Everywhere
- Secure remote printing via Home Assistant Cloud or direct IPPS
- Multi-architecture support (aarch64, amd64, armhf, armv7, i386)
- Web-based printer management interface
- Automated print job handling
- Persistent configuration and print queue
- SSL/TLS encryption for secure remote access

## Installation

1. Add our repository to your Home Assistant instance:
   ```
   https://github.com/samuelmukoti/ha-printer-relay
   ```

2. Search for "RelayPrint" in the Add-on store
3. Click Install
4. Configure the add-on (see Configuration section)
5. Start the add-on

## Configuration

Example configuration:

```yaml
ssl: true
certfile: fullchain.pem
keyfile: privkey.pem
remote_access: false
allowed_interfaces:
  - eth0
  - wlan0
printer_options:
  default_media: "A4"
  color_mode: "color"
  duplex: false
advanced:
  cups_timeout: 30
  job_retention: 24
  max_jobs: 100
```

### Option: `ssl`
- Enable/disable SSL for secure remote access
- Default: `true`

### Option: `certfile`
- SSL certificate file path
- Default: `fullchain.pem`

### Option: `keyfile`
- SSL private key file path
- Default: `privkey.pem`

### Option: `remote_access`
- Enable/disable remote printing access
- Default: `false`

### Option: `allowed_interfaces`
- List of network interfaces to listen on
- Default: `["eth0"]`

### Option: `printer_options`
Configuration for default printer settings:
- `default_media`: Default paper size (A4, Letter, etc.)
- `color_mode`: Default color mode (color/monochrome)
- `duplex`: Enable/disable double-sided printing

### Option: `advanced`
Advanced configuration options:
- `cups_timeout`: CUPS operation timeout in seconds
- `job_retention`: How long to keep completed jobs (hours)
- `max_jobs`: Maximum number of jobs in queue

## Network Ports

- 631/tcp: CUPS web interface and IPP/IPPS printing
- 5353/udp: Avahi/mDNS for printer discovery

## Security

- All remote access requires authentication
- SSL/TLS encryption for remote printing
- AppArmor profile included
- Access control via Home Assistant authentication

## Support

Need help? Here are some resources:

- [Documentation](https://github.com/yourusername/ha-printer-relay/wiki)
- [Issue Tracker](https://github.com/yourusername/ha-printer-relay/issues)
- [Discussion Forum](https://community.home-assistant.io/)

## Contributing

We welcome contributions! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Home Assistant community
- CUPS project
- Avahi project
- All our contributors

## Roadmap

- [ ] iOS companion app
- [ ] Android companion app
- [ ] WordPress plugin for automated printing
- [ ] Print job status notifications
- [ ] Enhanced printer management UI

