#!/command/with-contenv bashio
# shellcheck shell=bash
set -euo pipefail

bashio::log.info "Setting up CUPS persistent storage..."

# ==============================================================================
# Configure persistent storage for CUPS
# ==============================================================================
mkdir -p \
    /data/cups/ssl \
    /data/cups/ppd \
    /data/cups/interface \
    /data/cups/spool \
    /data/printers \
    /data/logs

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

# Initialize CUPS config if not present
if [ ! -f /data/cups/cupsd.conf ]; then
    cp /etc/cups.default/cupsd.conf /data/cups/
fi

# Ensure CUPS files exist
[ ! -f /data/cups/printers.conf ] && touch /data/cups/printers.conf
[ ! -f /data/cups/classes.conf ] && touch /data/cups/classes.conf

# Set ownership
chown -R root:root /data/cups
chown -R root:lp /data/cups/spool 2>/dev/null || true
chown -R root:lp /data/printers 2>/dev/null || true

# ==============================================================================
# Configure CUPS with options from config
# ==============================================================================
CUPS_ADMIN_USER=$(bashio::config 'cups_admin_user' 2>/dev/null || echo "admin")
LOG_LEVEL=$(bashio::config 'log_level' 2>/dev/null || echo "info")
MAX_JOBS=$(bashio::config 'advanced.max_jobs' 2>/dev/null || echo "100")

case "${LOG_LEVEL}" in
    debug|debug2|info|warn|warning|error|notice) ;;
    *) LOG_LEVEL="info" ;;
esac

[[ "${MAX_JOBS}" =~ ^[0-9]+$ ]] || MAX_JOBS="100"
[[ -z "${CUPS_ADMIN_USER}" ]] && CUPS_ADMIN_USER="admin"

{
    echo "LogLevel ${LOG_LEVEL}"
    echo "MaxLogSize 0"
    echo "PreserveJobHistory Yes"
    echo "PreserveJobFiles No"
    echo "MaxJobs ${MAX_JOBS}"
    echo "MaxJobTime 0"
    echo "Port 631"
    echo "Listen /run/cups/cups.sock"
    echo "Browsing On"
    echo "BrowseLocalProtocols dnssd"
    echo "DefaultAuthType Basic"
    echo "WebInterface Yes"
    echo ""
    echo "<Location />"
    echo "  Order allow,deny"
    echo "  Allow @LOCAL"
    echo "</Location>"
    echo ""
    echo "<Location /admin>"
    echo "  Order allow,deny"
    echo "  Allow @LOCAL"
    echo "</Location>"
    echo ""
    echo "<Location /admin/conf>"
    echo "  AuthType Default"
    echo "  Require user @SYSTEM ${CUPS_ADMIN_USER}"
    echo "  Order allow,deny"
    echo "  Allow @LOCAL"
    echo "</Location>"
    echo ""
    echo "<Policy default>"
    echo "  JobPrivateAccess default"
    echo "  JobPrivateValues default"
    echo "  SubscriptionPrivateAccess default"
    echo "  SubscriptionPrivateValues default"
    echo "  <Limit Create-Job Print-Job Print-URI Validate-Job>"
    echo "    Order deny,allow"
    echo "  </Limit>"
    echo "  <Limit Send-Document Send-URI Hold-Job Release-Job Restart-Job Purge-Jobs Set-Job-Attributes Create-Job-Subscription Renew-Subscription Cancel-Subscription Get-Notifications Reprocess-Job Cancel-Current-Job Suspend-Current-Job Resume-Job Cancel-My-Jobs Close-Job CUPS-Move-Job CUPS-Get-Document>"
    echo "    Require user @OWNER @SYSTEM"
    echo "    Order deny,allow"
    echo "  </Limit>"
    echo "  <Limit Cancel-Job Cancel-Jobs>"
    echo "    Require user @OWNER @SYSTEM"
    echo "    Order deny,allow"
    echo "  </Limit>"
    echo "  <Limit CUPS-Add-Modify-Printer CUPS-Delete-Printer CUPS-Add-Modify-Class CUPS-Delete-Class CUPS-Set-Default CUPS-Get-Devices>"
    echo "    AuthType Default"
    echo "    Require user @SYSTEM"
    echo "    Order deny,allow"
    echo "  </Limit>"
    echo "  <Limit All>"
    echo "    Order deny,allow"
    echo "  </Limit>"
    echo "</Policy>"
} > /data/cups/cupsd.conf

# Ensure directories exist for dbus, avahi, and cups
mkdir -p /run/dbus /run/avahi-daemon /run/cups

bashio::log.info "CUPS setup complete"
