#!/usr/bin/with-contenv bashio
# ==============================================================================
# Home Assistant Add-on: RelayPrint Server
# Configures persistent storage for CUPS
# ==============================================================================

# Create persistent directories if they don't exist
mkdir -p \
    /data/cups/ssl \
    /data/cups/ppd \
    /data/cups/interface \
    /data/cups/spool \
    /data/printers \
    /data/logs

# Set correct permissions
chmod -R 755 /data/cups
chmod 700 /data/cups/ssl
chmod 755 /data/cups/ppd
chmod 755 /data/cups/interface
chmod 770 /data/cups/spool
chmod 755 /data/printers
chmod 755 /data/logs

# Create symlinks for CUPS to use persistent storage
rm -rf /etc/cups
ln -s /data/cups /etc/cups

rm -rf /var/spool/cups
ln -s /data/cups/spool /var/spool/cups

# Initialize empty config files if they don't exist
if [ ! -f /data/cups/cupsd.conf ]; then
    cp /etc/cups.default/cupsd.conf /data/cups/
fi

if [ ! -f /data/cups/printers.conf ]; then
    touch /data/cups/printers.conf
fi

if [ ! -f /data/cups/classes.conf ]; then
    touch /data/cups/classes.conf
fi

# Set ownership
chown -R root:root /data/cups
chown -R root:lp /data/cups/spool
chown -R root:lp /data/printers

bashio::log.info "Persistent storage configuration completed" 