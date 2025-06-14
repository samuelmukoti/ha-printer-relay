<?php
/**
 * Default order template for PDF generation
 * 
 * @var WC_Order $order
 * @var Relay_Print_Template $this
 */

if (!defined('ABSPATH')) {
    exit;
}
?>
<style>
    .order-header {
        text-align: center;
        margin-bottom: 20px;
    }
    .order-info {
        margin-bottom: 20px;
    }
    .order-addresses {
        margin-bottom: 20px;
    }
    .address-block {
        margin-bottom: 10px;
    }
    .items-table {
        width: 100%;
        border-collapse: collapse;
        margin-bottom: 20px;
    }
    .items-table th,
    .items-table td {
        border: 1px solid #ddd;
        padding: 8px;
        text-align: left;
    }
    .items-table th {
        background-color: #f8f8f8;
    }
    .totals-table {
        width: 100%;
        margin-bottom: 20px;
    }
    .totals-table td {
        padding: 4px;
    }
    .totals-table .label {
        text-align: right;
    }
    .totals-table .amount {
        text-align: left;
        padding-left: 20px;
    }
    .footer {
        text-align: center;
        font-size: 12px;
        color: #666;
    }
</style>

<div class="order-header">
    <h1><?php echo esc_html(get_bloginfo('name')); ?></h1>
    <h2><?php printf(esc_html__('Order #%s', 'relay-print'), $order->get_order_number()); ?></h2>
    <p><?php echo esc_html($this->format_date($order->get_date_created())); ?></p>
</div>

<div class="order-info">
    <p><strong><?php esc_html_e('Order Status:', 'relay-print'); ?></strong> <?php echo esc_html(wc_get_order_status_name($order->get_status())); ?></p>
    <p><strong><?php esc_html_e('Payment Method:', 'relay-print'); ?></strong> <?php echo esc_html($order->get_payment_method_title()); ?></p>
</div>

<div class="order-addresses">
    <div class="address-block">
        <h3><?php esc_html_e('Billing Address', 'relay-print'); ?></h3>
        <?php echo wp_kses_post($this->format_address($order->get_address('billing'))); ?>
    </div>

    <?php if ($order->get_shipping_address_1()) : ?>
        <div class="address-block">
            <h3><?php esc_html_e('Shipping Address', 'relay-print'); ?></h3>
            <?php echo wp_kses_post($this->format_address($order->get_address('shipping'))); ?>
        </div>
    <?php endif; ?>
</div>

<table class="items-table">
    <thead>
        <tr>
            <th><?php esc_html_e('Product', 'relay-print'); ?></th>
            <th><?php esc_html_e('Quantity', 'relay-print'); ?></th>
            <th><?php esc_html_e('Price', 'relay-print'); ?></th>
            <th><?php esc_html_e('Total', 'relay-print'); ?></th>
        </tr>
    </thead>
    <tbody>
        <?php
        foreach ($order->get_items() as $item_id => $item) {
            $product = $item->get_product();
            ?>
            <tr>
                <td>
                    <?php
                    echo esc_html($item->get_name());
                    if ($product && $product->get_sku()) {
                        echo ' (' . esc_html($product->get_sku()) . ')';
                    }
                    
                    // Show variation data
                    if ($item->get_variation_id()) {
                        echo '<br><small>' . wc_get_formatted_variation($item->get_variation_data(), true) . '</small>';
                    }
                    ?>
                </td>
                <td><?php echo esc_html($item->get_quantity()); ?></td>
                <td><?php echo wp_kses_post($this->format_currency($order->get_item_subtotal($item, false, true), $order)); ?></td>
                <td><?php echo wp_kses_post($this->format_currency($order->get_line_subtotal($item, false, true), $order)); ?></td>
            </tr>
            <?php
        }
        ?>
    </tbody>
</table>

<table class="totals-table">
    <tr>
        <td class="label"><?php esc_html_e('Subtotal:', 'relay-print'); ?></td>
        <td class="amount"><?php echo wp_kses_post($this->format_currency($order->get_subtotal(), $order)); ?></td>
    </tr>
    
    <?php if ($order->get_shipping_total() > 0) : ?>
        <tr>
            <td class="label"><?php esc_html_e('Shipping:', 'relay-print'); ?></td>
            <td class="amount"><?php echo wp_kses_post($this->format_currency($order->get_shipping_total(), $order)); ?></td>
        </tr>
    <?php endif; ?>
    
    <?php foreach ($order->get_tax_totals() as $code => $tax) : ?>
        <tr>
            <td class="label"><?php echo esc_html($tax->label); ?>:</td>
            <td class="amount"><?php echo wp_kses_post($this->format_currency($tax->amount, $order)); ?></td>
        </tr>
    <?php endforeach; ?>
    
    <?php if ($order->get_total_discount() > 0) : ?>
        <tr>
            <td class="label"><?php esc_html_e('Discount:', 'relay-print'); ?></td>
            <td class="amount">-<?php echo wp_kses_post($this->format_currency($order->get_total_discount(), $order)); ?></td>
        </tr>
    <?php endif; ?>
    
    <tr>
        <td class="label"><strong><?php esc_html_e('Total:', 'relay-print'); ?></strong></td>
        <td class="amount"><strong><?php echo wp_kses_post($this->format_currency($order->get_total(), $order)); ?></strong></td>
    </tr>
</table>

<?php if ($order->get_customer_note()) : ?>
    <div class="order-notes">
        <h3><?php esc_html_e('Order Notes', 'relay-print'); ?></h3>
        <p><?php echo wp_kses_post(nl2br($order->get_customer_note())); ?></p>
    </div>
<?php endif; ?>

<div class="footer">
    <p><?php esc_html_e('Thank you for your order!', 'relay-print'); ?></p>
    <p><?php echo esc_html(get_bloginfo('name')); ?></p>
</div> 