<?php

if (!defined('ABSPATH')) {
    exit;
}

class Relay_Print_Settings {
    /**
     * Initialize settings
     */
    public function __construct() {
        add_action('admin_menu', array($this, 'add_settings_page'));
        add_action('admin_init', array($this, 'register_settings'));
    }

    /**
     * Add settings page to WooCommerce menu
     */
    public function add_settings_page() {
        add_submenu_page(
            'woocommerce',
            __('Relay Print Settings', 'relay-print'),
            __('Relay Print', 'relay-print'),
            'manage_woocommerce',
            'relay-print-settings',
            array($this, 'render_settings_page')
        );
    }

    /**
     * Register plugin settings
     */
    public function register_settings() {
        register_setting('relay_print_settings', 'relay_print_settings', array(
            'type' => 'array',
            'sanitize_callback' => array($this, 'sanitize_settings')
        ));

        add_settings_section(
            'relay_print_api_settings',
            __('API Settings', 'relay-print'),
            array($this, 'render_api_section'),
            'relay-print-settings'
        );

        add_settings_field(
            'ha_url',
            __('Home Assistant URL', 'relay-print'),
            array($this, 'render_text_field'),
            'relay-print-settings',
            'relay_print_api_settings',
            array('field' => 'ha_url')
        );

        add_settings_field(
            'api_secret',
            __('API Secret', 'relay-print'),
            array($this, 'render_text_field'),
            'relay-print-settings',
            'relay_print_api_settings',
            array('field' => 'api_secret', 'type' => 'password')
        );

        add_settings_section(
            'relay_print_order_settings',
            __('Order Settings', 'relay-print'),
            array($this, 'render_order_section'),
            'relay-print-settings'
        );

        add_settings_field(
            'auto_print_statuses',
            __('Auto-Print Order Statuses', 'relay-print'),
            array($this, 'render_status_checkboxes'),
            'relay-print-settings',
            'relay_print_order_settings'
        );

        add_settings_field(
            'print_template',
            __('Print Template', 'relay-print'),
            array($this, 'render_template_select'),
            'relay-print-settings',
            'relay_print_order_settings'
        );
    }

    /**
     * Render settings page
     */
    public function render_settings_page() {
        if (!current_user_can('manage_woocommerce')) {
            return;
        }

        // Test connection if requested
        if (isset($_POST['test_connection'])) {
            check_admin_referer('relay_print_test_connection');
            $this->test_connection();
        }

        ?>
        <div class="wrap">
            <h1><?php echo esc_html(get_admin_page_title()); ?></h1>
            
            <form action="options.php" method="post">
                <?php
                settings_fields('relay_print_settings');
                do_settings_sections('relay-print-settings');
                submit_button();
                ?>
            </form>

            <form action="" method="post" style="margin-top: 20px;">
                <?php wp_nonce_field('relay_print_test_connection'); ?>
                <input type="submit" name="test_connection" class="button button-secondary" 
                       value="<?php esc_attr_e('Test Connection', 'relay-print'); ?>">
            </form>
        </div>
        <?php
    }

    /**
     * Render API settings section
     */
    public function render_api_section() {
        echo '<p>' . esc_html__('Configure your Home Assistant printer relay connection settings.', 'relay-print') . '</p>';
    }

    /**
     * Render order settings section
     */
    public function render_order_section() {
        echo '<p>' . esc_html__('Configure when and how orders should be printed.', 'relay-print') . '</p>';
    }

    /**
     * Render text field
     */
    public function render_text_field($args) {
        $options = get_option('relay_print_settings', array());
        $field = $args['field'];
        $type = isset($args['type']) ? $args['type'] : 'text';
        $value = isset($options[$field]) ? $options[$field] : '';
        
        printf(
            '<input type="%s" id="%s" name="relay_print_settings[%s]" value="%s" class="regular-text">',
            esc_attr($type),
            esc_attr($field),
            esc_attr($field),
            esc_attr($value)
        );
    }

    /**
     * Render status checkboxes
     */
    public function render_status_checkboxes() {
        $options = get_option('relay_print_settings', array());
        $auto_print_statuses = isset($options['auto_print_statuses']) ? $options['auto_print_statuses'] : array();
        
        $order_statuses = wc_get_order_statuses();
        foreach ($order_statuses as $status => $label) {
            $status = str_replace('wc-', '', $status);
            printf(
                '<label><input type="checkbox" name="relay_print_settings[auto_print_statuses][]" value="%s" %s> %s</label><br>',
                esc_attr($status),
                checked(in_array($status, $auto_print_statuses), true, false),
                esc_html($label)
            );
        }
    }

    /**
     * Render template select
     */
    public function render_template_select() {
        $options = get_option('relay_print_settings', array());
        $current_template = isset($options['print_template']) ? $options['print_template'] : 'default';
        
        $templates = array(
            'default' => __('Default Template', 'relay-print'),
            'compact' => __('Compact Template', 'relay-print'),
            'detailed' => __('Detailed Template', 'relay-print')
        );

        echo '<select name="relay_print_settings[print_template]">';
        foreach ($templates as $value => $label) {
            printf(
                '<option value="%s" %s>%s</option>',
                esc_attr($value),
                selected($current_template, $value, false),
                esc_html($label)
            );
        }
        echo '</select>';
    }

    /**
     * Sanitize settings
     */
    public function sanitize_settings($input) {
        $sanitized = array();

        if (isset($input['ha_url'])) {
            $sanitized['ha_url'] = esc_url_raw(trim($input['ha_url']));
        }

        if (isset($input['api_secret'])) {
            $sanitized['api_secret'] = sanitize_text_field($input['api_secret']);
        }

        if (isset($input['auto_print_statuses']) && is_array($input['auto_print_statuses'])) {
            $sanitized['auto_print_statuses'] = array_map('sanitize_text_field', $input['auto_print_statuses']);
        }

        if (isset($input['print_template'])) {
            $sanitized['print_template'] = sanitize_text_field($input['print_template']);
        }

        return $sanitized;
    }

    /**
     * Test connection to Home Assistant
     */
    private function test_connection() {
        $options = get_option('relay_print_settings', array());
        
        if (empty($options['ha_url']) || empty($options['api_secret'])) {
            add_settings_error(
                'relay_print_settings',
                'connection_test',
                __('Please configure Home Assistant URL and API Secret first.', 'relay-print'),
                'error'
            );
            return;
        }

        $api = new Relay_Print_API();
        $result = $api->test_connection();

        if (is_wp_error($result)) {
            add_settings_error(
                'relay_print_settings',
                'connection_test',
                sprintf(
                    __('Connection test failed: %s', 'relay-print'),
                    $result->get_error_message()
                ),
                'error'
            );
        } else {
            add_settings_error(
                'relay_print_settings',
                'connection_test',
                __('Connection test successful!', 'relay-print'),
                'success'
            );
        }
    }

    /**
     * Get auto-print statuses
     */
    public function get_auto_print_statuses() {
        $options = get_option('relay_print_settings', array());
        return isset($options['auto_print_statuses']) ? $options['auto_print_statuses'] : array();
    }

    /**
     * Get print template
     */
    public function get_print_template() {
        $options = get_option('relay_print_settings', array());
        return isset($options['print_template']) ? $options['print_template'] : 'default';
    }

    /**
     * Get API settings
     */
    public function get_api_settings() {
        $options = get_option('relay_print_settings', array());
        return array(
            'ha_url' => isset($options['ha_url']) ? $options['ha_url'] : '',
            'api_secret' => isset($options['api_secret']) ? $options['api_secret'] : '',
        );
    }
} 