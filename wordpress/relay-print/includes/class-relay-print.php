<?php

if (!defined('ABSPATH')) {
    exit;
}

class Relay_Print {
    /**
     * @var Relay_Print_Settings
     */
    private $settings;

    /**
     * @var Relay_Print_Template
     */
    private $template;

    /**
     * @var Relay_Print_API
     */
    private $api;

    /**
     * Initialize the plugin
     */
    public function init() {
        // Initialize components
        $this->settings = new Relay_Print_Settings();
        $this->template = new Relay_Print_Template();
        $this->api = new Relay_Print_API();

        // Register hooks
        add_action('admin_enqueue_scripts', array($this, 'enqueue_admin_scripts'));
        add_action('woocommerce_order_status_changed', array($this, 'handle_order_status_change'), 10, 4);
        add_action('add_meta_boxes', array($this, 'add_order_meta_box'));
        add_action('wp_ajax_relay_print_order', array($this, 'ajax_print_order'));
    }

    /**
     * Enqueue admin scripts and styles
     */
    public function enqueue_admin_scripts($hook) {
        if ('woocommerce_page_wc-orders' !== $hook && 'post.php' !== $hook) {
            return;
        }

        wp_enqueue_style(
            'relay-print-admin',
            RELAY_PRINT_PLUGIN_URL . 'assets/css/admin.css',
            array(),
            RELAY_PRINT_VERSION
        );

        wp_enqueue_script(
            'relay-print-admin',
            RELAY_PRINT_PLUGIN_URL . 'assets/js/admin.js',
            array('jquery'),
            RELAY_PRINT_VERSION,
            true
        );

        wp_localize_script('relay-print-admin', 'relayPrintAdmin', array(
            'ajax_url' => admin_url('admin-ajax.php'),
            'nonce' => wp_create_nonce('relay-print-nonce'),
        ));
    }

    /**
     * Handle order status changes
     */
    public function handle_order_status_change($order_id, $old_status, $new_status, $order) {
        $auto_print_statuses = $this->settings->get_auto_print_statuses();
        
        if (in_array($new_status, $auto_print_statuses)) {
            $this->print_order($order_id);
        }
    }

    /**
     * Add meta box to order page
     */
    public function add_order_meta_box() {
        add_meta_box(
            'relay-print-actions',
            __('Relay Print Actions', 'relay-print'),
            array($this, 'render_order_meta_box'),
            'shop_order',
            'side',
            'default'
        );
    }

    /**
     * Render order meta box content
     */
    public function render_order_meta_box($post) {
        $order = wc_get_order($post->ID);
        if (!$order) {
            return;
        }

        $print_history = get_post_meta($post->ID, '_relay_print_history', true) ?: array();
        
        include RELAY_PRINT_PLUGIN_DIR . 'templates/order-meta-box.php';
    }

    /**
     * Handle AJAX print request
     */
    public function ajax_print_order() {
        check_ajax_referer('relay-print-nonce', 'nonce');

        if (!current_user_can('edit_shop_orders')) {
            wp_send_json_error(array('message' => __('Permission denied', 'relay-print')));
            return;
        }

        $order_id = isset($_POST['order_id']) ? intval($_POST['order_id']) : 0;
        if (!$order_id) {
            wp_send_json_error(array('message' => __('Invalid order ID', 'relay-print')));
            return;
        }

        $result = $this->print_order($order_id);
        if (is_wp_error($result)) {
            wp_send_json_error(array('message' => $result->get_error_message()));
            return;
        }

        wp_send_json_success(array(
            'message' => __('Print job sent successfully', 'relay-print'),
            'job_id' => $result
        ));
    }

    /**
     * Print an order
     */
    private function print_order($order_id) {
        $order = wc_get_order($order_id);
        if (!$order) {
            return new WP_Error('invalid_order', __('Invalid order', 'relay-print'));
        }

        // Generate PDF content
        $content = $this->template->generate_order_pdf($order);
        if (is_wp_error($content)) {
            return $content;
        }

        // Send to printer via API
        $result = $this->api->send_print_job($content);
        if (is_wp_error($result)) {
            return $result;
        }

        // Record print history
        $history = get_post_meta($order_id, '_relay_print_history', true) ?: array();
        $history[] = array(
            'timestamp' => current_time('mysql'),
            'user' => get_current_user_id(),
            'job_id' => $result
        );
        update_post_meta($order_id, '_relay_print_history', $history);

        return $result;
    }
} 