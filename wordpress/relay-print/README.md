# Relay Print for WooCommerce

A WordPress plugin that enables automatic printing of WooCommerce orders through a Home Assistant printer relay.

## Features

- Automatically print orders when they reach specific statuses
- Manual print button on order admin page
- Multiple print templates (default, compact, detailed)
- Print history tracking
- Secure API integration with Home Assistant
- Customizable settings

## Requirements

- WordPress 5.8 or higher
- WooCommerce 5.0 or higher
- PHP 7.4 or higher
- Home Assistant instance with printer relay add-on installed

## Installation

1. Download the plugin zip file
2. Go to WordPress admin > Plugins > Add New
3. Click "Upload Plugin" and select the downloaded zip file
4. Click "Install Now" and then "Activate"

## Configuration

1. Go to WooCommerce > Relay Print in the WordPress admin menu
2. Enter your Home Assistant URL and API Secret
3. Configure which order statuses should trigger automatic printing
4. Select your preferred print template
5. Click "Save Changes"
6. Use the "Test Connection" button to verify the setup

## Print Templates

The plugin comes with three built-in templates:

1. **Default Template**
   - Full order details
   - Customer information
   - Product list with prices
   - Totals and taxes

2. **Compact Template**
   - Basic order information
   - Product list without prices
   - Shipping address

3. **Detailed Template**
   - All order details
   - Customer notes
   - Payment information
   - Full tax breakdown

## Usage

### Automatic Printing

Orders will automatically print when they reach any of the selected statuses in the plugin settings.

### Manual Printing

1. Go to WooCommerce > Orders
2. Open an order
3. Find the "Relay Print Actions" meta box
4. Click "Print Order"

### Print History

The print history for each order is displayed in the "Relay Print Actions" meta box, showing:
- Print date and time
- User who initiated the print
- Print job ID

## Development

### Setup

1. Clone the repository
2. Run `composer install` to install dependencies
3. Run `composer test` to run unit tests
4. Run `composer phpcs` to check coding standards

### Directory Structure

```
relay-print/
├── assets/
│   ├── css/
│   │   └── admin.css
│   └── js/
│       └── admin.js
├── includes/
│   ├── class-relay-print.php
│   ├── class-relay-print-settings.php
│   ├── class-relay-print-template.php
│   └── class-relay-print-api.php
├── templates/
│   ├── order-default.php
│   ├── order-compact.php
│   ├── order-detailed.php
│   └── order-meta-box.php
├── vendor/
├── composer.json
├── relay-print.php
└── README.md
```

## Support

For issues and feature requests, please use the [GitHub issue tracker](https://github.com/samuelmukoti/ha-printer-relay/issues).

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests and coding standards checks
5. Submit a pull request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details. 