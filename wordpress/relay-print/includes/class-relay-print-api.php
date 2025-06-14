<?php

if (!defined('ABSPATH')) {
    exit;
}

class Relay_Print_API {
    /**
     * @var array
     */
    private $settings;

    /**
     * Initialize API
     */
    public function __construct() {
        $settings = new Relay_Print_Settings();
        $this->settings = $settings->get_api_settings();
    }

    /**
     * Send print job to Home Assistant
     */
    public function send_print_job($content) {
        if (empty($this->settings['ha_url']) || empty($this->settings['api_secret'])) {
            return new WP_Error(
                'missing_settings',
                __('Home Assistant URL and API Secret must be configured.', 'relay-print')
            );
        }

        $url = trailingslashit($this->settings['ha_url']) . 'api/print';
        
        $response = wp_remote_post($url, array(
            'headers' => array(
                'Content-Type' => 'application/pdf',
                'X-API-Secret' => $this->settings['api_secret']
            ),
            'body' => $content,
            'timeout' => 30
        ));

        if (is_wp_error($response)) {
            return $response;
        }

        $status_code = wp_remote_retrieve_response_code($response);
        $body = wp_remote_retrieve_body($response);
        $data = json_decode($body, true);

        if ($status_code !== 200) {
            return new WP_Error(
                'api_error',
                isset($data['message']) 
                    ? $data['message'] 
                    : __('Failed to send print job to Home Assistant.', 'relay-print')
            );
        }

        return isset($data['job_id']) ? $data['job_id'] : '';
    }

    /**
     * Get print job status
     */
    public function get_job_status($job_id) {
        if (empty($this->settings['ha_url']) || empty($this->settings['api_secret'])) {
            return new WP_Error(
                'missing_settings',
                __('Home Assistant URL and API Secret must be configured.', 'relay-print')
            );
        }

        $url = trailingslashit($this->settings['ha_url']) . 'api/print/' . urlencode($job_id) . '/status';
        
        $response = wp_remote_get($url, array(
            'headers' => array(
                'X-API-Secret' => $this->settings['api_secret']
            ),
            'timeout' => 10
        ));

        if (is_wp_error($response)) {
            return $response;
        }

        $status_code = wp_remote_retrieve_response_code($response);
        $body = wp_remote_retrieve_body($response);
        $data = json_decode($body, true);

        if ($status_code !== 200) {
            return new WP_Error(
                'api_error',
                isset($data['message']) 
                    ? $data['message'] 
                    : __('Failed to get print job status from Home Assistant.', 'relay-print')
            );
        }

        return isset($data['status']) ? $data['status'] : '';
    }

    /**
     * Test connection to Home Assistant
     */
    public function test_connection() {
        if (empty($this->settings['ha_url']) || empty($this->settings['api_secret'])) {
            return new WP_Error(
                'missing_settings',
                __('Home Assistant URL and API Secret must be configured.', 'relay-print')
            );
        }

        $url = trailingslashit($this->settings['ha_url']) . 'api/print/test';
        
        $response = wp_remote_get($url, array(
            'headers' => array(
                'X-API-Secret' => $this->settings['api_secret']
            ),
            'timeout' => 10
        ));

        if (is_wp_error($response)) {
            return $response;
        }

        $status_code = wp_remote_retrieve_response_code($response);
        
        if ($status_code !== 200) {
            return new WP_Error(
                'api_error',
                __('Failed to connect to Home Assistant.', 'relay-print')
            );
        }

        return true;
    }
} 