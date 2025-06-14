<?php
/**
 * Plugin Name: Relay Print for WooCommerce
 * Plugin URI: https://github.com/samuelmukoti/ha-printer-relay
 * Description: Automatically print WooCommerce orders through Home Assistant printer relay
 * Version: 1.0.0
 * Author: Samuel Mukoti
 * Author URI: https://github.com/samuelmukoti
 * License: Apache-2.0
 * License URI: https://www.apache.org/licenses/LICENSE-2.0
 * Text Domain: relay-print
 * Domain Path: /languages
 * Requires at least: 5.8
 * Requires PHP: 7.4
 * WC requires at least: 5.0
 * WC tested up to: 8.3
 */

if (!defined('ABSPATH')) {
    exit; // Exit if accessed directly
}

// Define plugin constants
define('RELAY_PRINT_VERSION', '1.0.0');
define('RELAY_PRINT_PLUGIN_DIR', plugin_dir_path(__FILE__));
define('RELAY_PRINT_PLUGIN_URL', plugin_dir_url(__FILE__));

// Ensure WooCommerce is active
if (!in_array('woocommerce/woocommerce.php', apply_filters('active_plugins', get_option('active_plugins')))) {
    return;
}

// Include required files
require_once RELAY_PRINT_PLUGIN_DIR . 'includes/class-relay-print.php';
require_once RELAY_PRINT_PLUGIN_DIR . 'includes/class-relay-print-settings.php';
require_once RELAY_PRINT_PLUGIN_DIR . 'includes/class-relay-print-template.php';
require_once RELAY_PRINT_PLUGIN_DIR . 'includes/class-relay-print-api.php';

// Initialize the plugin
function relay_print_init() {
    $plugin = new Relay_Print();
    $plugin->init();
}
add_action('plugins_loaded', 'relay_print_init'); 