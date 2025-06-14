<?php
/**
 * Order meta box template
 * 
 * @var WC_Order $order
 * @var array $print_history
 */

if (!defined('ABSPATH')) {
    exit;
}
?>

<div class="relay-print-meta-box">
    <p>
        <button type="button" class="button button-primary print-order" data-order-id="<?php echo esc_attr($order->get_id()); ?>">
            <?php esc_html_e('Print Order', 'relay-print'); ?>
        </button>
        <span class="spinner" style="float: none; margin: 4px 10px;"></span>
    </p>

    <?php if (!empty($print_history)) : ?>
        <h4><?php esc_html_e('Print History', 'relay-print'); ?></h4>
        <table class="widefat">
            <thead>
                <tr>
                    <th><?php esc_html_e('Date', 'relay-print'); ?></th>
                    <th><?php esc_html_e('User', 'relay-print'); ?></th>
                    <th><?php esc_html_e('Job ID', 'relay-print'); ?></th>
                </tr>
            </thead>
            <tbody>
                <?php foreach (array_reverse($print_history) as $entry) : ?>
                    <tr>
                        <td>
                            <?php
                            $date = new DateTime($entry['timestamp']);
                            echo esc_html($date->format(get_option('date_format') . ' ' . get_option('time_format')));
                            ?>
                        </td>
                        <td>
                            <?php
                            $user = get_user_by('id', $entry['user']);
                            echo esc_html($user ? $user->display_name : __('Unknown', 'relay-print'));
                            ?>
                        </td>
                        <td><?php echo esc_html($entry['job_id']); ?></td>
                    </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    <?php endif; ?>
</div>

<script type="text/javascript">
jQuery(function($) {
    $('.relay-print-meta-box .print-order').on('click', function(e) {
        e.preventDefault();
        
        var $button = $(this);
        var $spinner = $button.next('.spinner');
        var orderId = $button.data('order-id');
        
        $button.prop('disabled', true);
        $spinner.addClass('is-active');
        
        $.ajax({
            url: ajaxurl,
            type: 'POST',
            data: {
                action: 'relay_print_order',
                order_id: orderId,
                nonce: relayPrintAdmin.nonce
            },
            success: function(response) {
                if (response.success) {
                    alert(response.data.message);
                    location.reload();
                } else {
                    alert(response.data.message || '<?php esc_html_e('Failed to print order.', 'relay-print'); ?>');
                }
            },
            error: function() {
                alert('<?php esc_html_e('Failed to print order. Please try again.', 'relay-print'); ?>');
            },
            complete: function() {
                $button.prop('disabled', false);
                $spinner.removeClass('is-active');
            }
        });
    });
});
</script> 