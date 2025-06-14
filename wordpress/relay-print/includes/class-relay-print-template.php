<?php

if (!defined('ABSPATH')) {
    exit;
}

class Relay_Print_Template {
    /**
     * @var Relay_Print_Settings
     */
    private $settings;

    /**
     * Initialize template system
     */
    public function __construct() {
        $this->settings = new Relay_Print_Settings();
        
        // Load TCPDF if not already loaded
        if (!class_exists('TCPDF')) {
            require_once RELAY_PRINT_PLUGIN_DIR . 'vendor/tecnickcom/tcpdf/tcpdf.php';
        }
    }

    /**
     * Generate PDF for order
     */
    public function generate_order_pdf($order) {
        if (!$order) {
            return new WP_Error('invalid_order', __('Invalid order.', 'relay-print'));
        }

        try {
            // Create new PDF document
            $pdf = new TCPDF(PDF_PAGE_ORIENTATION, PDF_UNIT, PDF_PAGE_FORMAT, true, 'UTF-8', false);

            // Set document information
            $pdf->SetCreator(wp_get_current_user()->display_name);
            $pdf->SetAuthor(get_bloginfo('name'));
            $pdf->SetTitle(sprintf(__('Order #%s', 'relay-print'), $order->get_order_number()));

            // Remove header/footer
            $pdf->setPrintHeader(false);
            $pdf->setPrintFooter(false);

            // Set margins
            $pdf->SetMargins(15, 15, 15);

            // Add a page
            $pdf->AddPage();

            // Get template content based on settings
            $template = $this->settings->get_print_template();
            $content = $this->get_template_content($order, $template);

            // Add content to PDF
            $pdf->writeHTML($content, true, false, true, false, '');

            // Close and return PDF document
            return $pdf->Output('', 'S');

        } catch (Exception $e) {
            return new WP_Error(
                'pdf_generation_error',
                sprintf(__('Failed to generate PDF: %s', 'relay-print'), $e->getMessage())
            );
        }
    }

    /**
     * Get template content
     */
    private function get_template_content($order, $template) {
        $template_file = RELAY_PRINT_PLUGIN_DIR . 'templates/order-' . $template . '.php';
        
        if (!file_exists($template_file)) {
            $template_file = RELAY_PRINT_PLUGIN_DIR . 'templates/order-default.php';
        }

        ob_start();
        include $template_file;
        return ob_get_clean();
    }

    /**
     * Format currency
     */
    private function format_currency($amount, $order) {
        return strip_tags(wc_price($amount, array('currency' => $order->get_currency())));
    }

    /**
     * Format address
     */
    private function format_address($address) {
        return WC()->countries->get_formatted_address($address);
    }

    /**
     * Format date
     */
    private function format_date($date) {
        return wc_format_datetime($date);
    }
} 