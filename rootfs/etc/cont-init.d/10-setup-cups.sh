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
CUPS_ADMIN_PASSWORD=$(bashio::config 'cups_admin_password' 2>/dev/null || echo "changeme")
LOG_LEVEL=$(bashio::config 'log_level' 2>/dev/null || echo "info")
MAX_JOBS=$(bashio::config 'advanced.max_jobs' 2>/dev/null || echo "100")

# Create CUPS admin user if it doesn't exist and add to lpadmin group
if ! id "${CUPS_ADMIN_USER}" &>/dev/null; then
    bashio::log.info "Creating CUPS admin user: ${CUPS_ADMIN_USER}"
    adduser -D -H -s /sbin/nologin "${CUPS_ADMIN_USER}" 2>/dev/null || true
fi

# Set the password for the admin user
echo "${CUPS_ADMIN_USER}:${CUPS_ADMIN_PASSWORD}" | chpasswd 2>/dev/null || true

# Add user to lpadmin and sys groups for CUPS administration
addgroup "${CUPS_ADMIN_USER}" lpadmin 2>/dev/null || true
addgroup "${CUPS_ADMIN_USER}" lp 2>/dev/null || true
addgroup "${CUPS_ADMIN_USER}" sys 2>/dev/null || true

# Ensure lpadmin group exists
addgroup -S lpadmin 2>/dev/null || true

# Add root to lpadmin group so lpadmin command works
addgroup root lpadmin 2>/dev/null || true

case "${LOG_LEVEL}" in
    debug|debug2|info|warn|warning|error|notice) ;;
    *) LOG_LEVEL="info" ;;
esac

[[ "${MAX_JOBS}" =~ ^[0-9]+$ ]] || MAX_JOBS="100"
[[ -z "${CUPS_ADMIN_USER}" ]] && CUPS_ADMIN_USER="admin"

# Generate a permissive cupsd.conf for container use
# Authentication is handled by Home Assistant Ingress, so we allow local operations
cat > /data/cups/cupsd.conf << CUPSCONF
# Log settings
LogLevel ${LOG_LEVEL}
MaxLogSize 0
ErrorPolicy retry-job

# Job settings
PreserveJobHistory Yes
PreserveJobFiles No
MaxJobs ${MAX_JOBS}
MaxJobTime 0

# Network settings - listen on all interfaces
Port 631
Listen /run/cups/cups.sock

# Browsing/Discovery
Browsing On
BrowseLocalProtocols dnssd

# Security - permissive for container use (HA Ingress handles auth)
# Root and lpadmin group members can administer
SystemGroup root lpadmin wheel sys

# Allow all local access - container is already protected by HA auth
<Location />
  Order allow,deny
  Allow all
</Location>

<Location /admin>
  Order allow,deny
  Allow all
</Location>

<Location /admin/conf>
  Order allow,deny
  Allow all
</Location>

# Permissive policy - no authentication required for any operations
# This is safe because the container is isolated and protected by HA Ingress
<Policy default>
  JobPrivateAccess default
  JobPrivateValues default
  SubscriptionPrivateAccess default
  SubscriptionPrivateValues default

  <Limit All>
    Order allow,deny
    Allow all
  </Limit>
</Policy>
CUPSCONF

bashio::log.info "Generated permissive CUPS configuration for container use"

# Ensure directories exist for dbus, avahi, and cups
mkdir -p /run/dbus /run/avahi-daemon /run/cups

bashio::log.info "CUPS setup complete"
