jQuery(function($) {
    'use strict';

    // Handle print button click in order meta box
    $('.relay-print-meta-box .print-order').on('click', function(e) {
        e.preventDefault();
        
        var $button = $(this);
        var $spinner = $button.next('.spinner');
        var orderId = $button.data('order-id');
        
        if ($button.prop('disabled')) {
            return;
        }
        
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
                    alert(response.data.message || 'Failed to print order.');
                }
            },
            error: function() {
                alert('Failed to print order. Please try again.');
            },
            complete: function() {
                $button.prop('disabled', false);
                $spinner.removeClass('is-active');
            }
        });
    });

    // Handle test connection button click in settings
    $('.relay-print-settings .test-connection').on('click', function(e) {
        e.preventDefault();
        
        var $button = $(this);
        var $spinner = $button.next('.spinner');
        
        if ($button.prop('disabled')) {
            return;
        }
        
        $button.prop('disabled', true);
        $spinner.addClass('is-active');
        
        $.ajax({
            url: ajaxurl,
            type: 'POST',
            data: {
                action: 'relay_print_test_connection',
                nonce: relayPrintAdmin.nonce
            },
            success: function(response) {
                if (response.success) {
                    alert('Connection test successful!');
                } else {
                    alert(response.data.message || 'Connection test failed.');
                }
            },
            error: function() {
                alert('Connection test failed. Please try again.');
            },
            complete: function() {
                $button.prop('disabled', false);
                $spinner.removeClass('is-active');
            }
        });
    });

    // Handle template selection change
    $('.relay-print-settings select[name="relay_print_settings[print_template]"]').on('change', function() {
        var template = $(this).val();
        var $previewFrame = $('#template-preview');
        
        if ($previewFrame.length) {
            $previewFrame.attr('src', 'admin-ajax.php?action=relay_print_preview_template&template=' + template);
        }
    });

    // Initialize tooltips
    $('.relay-print-settings .help-tip').tipTip({
        'attribute': 'data-tip',
        'fadeIn': 50,
        'fadeOut': 50,
        'delay': 200
    });
}); 