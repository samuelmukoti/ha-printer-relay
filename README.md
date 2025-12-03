# RelayPrint - Home Assistant Printer Server Add-on

A Home Assistant add-on that replaces Google Cloud Print functionality by providing local and remote printing capabilities through CUPS and Avahi.

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Support-yellow?style=flat&logo=buy-me-a-coffee)](https://buymeacoffee.com/smukoti)

## Features

- Local printer sharing via AirPrint and IPP Everywhere
- Secure remote printing via Home Assistant Ingress
- Multi-architecture support (aarch64, amd64, armhf, armv7, i386)
- Web-based printer management interface (CUPS)
- Android companion app for mobile printing
- Automated print job handling
- Persistent configuration and print queue

## Installation

1. Add the repository to your Home Assistant instance:
   - Navigate to **Settings** -> **Add-ons** -> **Add-on Store**
   - Click the 3-dots menu (⋮) in the top right -> **Repositories**
   - Add this repository URL:
     ```
     https://github.com/samuelmukoti/ha-printer-relay
     ```

2. Search for "RelayPrint" in the Add-on store
3. Click **Install**
4. Configure the add-on (see Configuration section)
5. Start the add-on

## Configuration

Example configuration:

```yaml
log_level: info
cups_admin_user: admin
cups_admin_password: changeme
printer_options:
  default_media: A4
  color_mode: color
  duplex: false
advanced:
  cups_timeout: 30
  job_retention: 24
  max_jobs: 100
```

### Options

| Option | Description | Default |
|--------|-------------|---------|
| `log_level` | Logging verbosity (trace/debug/info/notice/warning/error/fatal) | `info` |
| `cups_admin_user` | Username for CUPS admin interface | `admin` |
| `cups_admin_password` | Password for CUPS admin interface | `changeme` |
| `printer_options.default_media` | Default paper size (A4/Letter/Legal) | `A4` |
| `printer_options.color_mode` | Default color mode (color/monochrome) | `color` |
| `printer_options.duplex` | Enable double-sided printing by default | `false` |
| `advanced.cups_timeout` | CUPS operation timeout in seconds | `30` |
| `advanced.job_retention` | How long to keep completed jobs (hours) | `24` |
| `advanced.max_jobs` | Maximum number of jobs in queue | `100` |

## Network Ports

- **631/tcp**: CUPS web interface and IPP/IPPS printing
- **5353/udp**: Avahi/mDNS for printer discovery

## Mobile App

An Android companion app is available for printing from your mobile device. The app connects to your Home Assistant instance and allows you to:

- Discover available printers
- Submit print jobs from any app via Android's print system
- Monitor print queue status
- Configure default print settings

## Troubleshooting

### Add-on won't start
- Check the add-on logs for error messages
- Ensure no other service is using ports 631 or 5353
- Try restarting Home Assistant

### Printer not discovered
- Ensure your printer is on the same network
- Check that Avahi/mDNS is not blocked by your firewall
- Try adding the printer manually via the CUPS web interface

### Print jobs failing
- Verify the printer is online and has paper/ink
- Check CUPS logs in the add-on for specific errors
- Ensure the correct printer driver is installed

## Development

For development instructions, please see [DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Support

- [Issue Tracker](https://github.com/samuelmukoti/ha-printer-relay/issues)
- [Home Assistant Community](https://community.home-assistant.io/)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Author

**Samuel Mukoti** - [GitHub](https://github.com/samuelmukoti)

If you find this project useful, consider supporting its development:

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Support-yellow?style=for-the-badge&logo=buy-me-a-coffee)](https://buymeacoffee.com/smukoti)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Home Assistant community
- CUPS project
- Avahi project

## Roadmap

- [x] Android companion app
- [ ] iOS companion app
- [ ] Print job status notifications
- [ ] Enhanced printer management UI
