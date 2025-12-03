# Printer Setup Guide

This guide explains how to set up and configure printers with the RelayPrint add-on for Home Assistant.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation](#installation)
3. [Adding a Printer](#adding-a-printer)
   - [USB Printers](#usb-printers)
   - [Network Printers](#network-printers)
4. [Printer Configuration](#printer-configuration)
5. [Testing the Setup](#testing-the-setup)
6. [Troubleshooting](#troubleshooting)

## Prerequisites

Before setting up a printer, ensure you have:

- Home Assistant OS or Supervised installation
- RelayPrint add-on installed and running
- Physical access to your printer (for USB printers)
- Network connectivity (for network printers)
- Printer manufacturer's drivers (if not using generic drivers)

## Installation

1. Open Home Assistant web interface
2. Navigate to Settings → Add-ons
3. Click on "Add-on Store"
4. Search for "RelayPrint"
5. Click "Install"
6. Wait for installation to complete
7. Start the add-on

## Adding a Printer

### USB Printers

1. Connect your printer to your Home Assistant device via USB
2. Open the RelayPrint web interface (via Home Assistant ingress)
3. Click "Add Printer"
4. Select "Local Printers" tab
5. Your USB printer should appear automatically
6. Click "Add This Printer"
7. Follow the setup wizard:
   - Choose a name (e.g., "Office-Printer")
   - Select the appropriate driver
   - Configure default settings
   - Click "Add Printer"

### Network Printers

1. Ensure your printer is connected to your network
2. Open the RelayPrint web interface
3. Click "Add Printer"
4. Select "Network Printers" tab
5. Choose discovery method:
   - "Find Network Printer" (automatic discovery)
   - "Enter Network Printer Address" (manual entry)
6. For manual entry:
   - Protocol: Choose IPP/IPPS for modern printers
   - Address: Enter IP address or hostname
   - Port: Usually 631 for IPP
7. Follow the setup wizard as with USB printers

## Printer Configuration

After adding a printer, configure these important settings:

1. **General Settings**
   - Description: Meaningful name for the printer
   - Location: Physical location (e.g., "Home Office")
   - Share This Printer: Enable for network access

2. **Policy Settings**
   - Error Policy: How to handle errors
   - Operation Policy: Who can use the printer
   - Banner Policy: Whether to print banner pages

3. **Print Settings**
   - Media Size: Default paper size (e.g., A4, Letter)
   - Media Type: Paper type
   - Print Quality: Draft, Normal, High
   - Color Mode: Color or Grayscale
   - Duplex: Enable for double-sided printing

4. **Advanced Settings**
   - Job Priority: Default priority for print jobs
   - Error Handling: How to handle specific errors
   - Printer State: Enable/Disable printer

## Testing the Setup

1. **Print Test Page**
   - Click "Maintenance" dropdown
   - Select "Print Test Page"
   - Check the output for:
     - Print quality
     - Alignment
     - Color accuracy (if applicable)

2. **Test from Different Devices**
   - Print from Home Assistant device
   - Print from mobile devices (iOS/Android)
   - Print from remote locations (if configured)

## Troubleshooting

### Common Issues

1. **Printer Not Found**
   - Check USB/network connection
   - Verify printer is powered on
   - Confirm network settings
   - Check firewall rules

2. **Driver Problems**
   - Try generic drivers first
   - Download manufacturer-specific drivers
   - Check driver compatibility

3. **Print Quality Issues**
   - Clean print heads
   - Check ink/toner levels
   - Verify media settings
   - Run printer alignment

4. **Network Connectivity**
   - Verify printer IP address
   - Check network stability
   - Test direct connection
   - Review firewall settings

### Getting Help

If you encounter issues:

1. Check the add-on logs in Home Assistant
2. Review CUPS error logs
3. Visit our GitHub repository
4. Open an issue with:
   - Detailed problem description
   - Log files
   - Configuration details
   - Steps to reproduce

## Security Considerations

1. **Access Control**
   - Use strong passwords
   - Limit network exposure
   - Configure user permissions

2. **Network Security**
   - Enable HTTPS/SSL
   - Use secure protocols
   - Restrict remote access

3. **Data Protection**
   - Clear print queues
   - Secure sensitive documents
   - Monitor printer usage

## Maintenance

Regular maintenance tasks:

1. **Software Updates**
   - Keep add-on updated
   - Update printer firmware
   - Check driver updates

2. **Hardware Maintenance**
   - Clean printer regularly
   - Check consumables
   - Verify connections

3. **Queue Management**
   - Monitor print queue
   - Clear stuck jobs
   - Check job history

## Advanced Features

1. **Print Quotas**
   - Set user limits
   - Track usage
   - Generate reports

2. **Automated Tasks**
   - Schedule maintenance
   - Auto-clear queues
   - Job notifications

3. **Integration**
   - Home Assistant automations
   - Mobile printing
   - Cloud services

## Support

For additional support:

- Documentation: [GitHub Wiki](https://github.com/samuelmukoti/ha-printer-relay/wiki)
- Community: [Home Assistant Community](https://community.home-assistant.io)
- Issues: [GitHub Issues](https://github.com/samuelmukoti/ha-printer-relay/issues)
- Updates: [Release Notes](https://github.com/samuelmukoti/ha-printer-relay/releases) 