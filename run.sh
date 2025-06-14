#!/usr/bin/with-contenv bashio
# ==============================================================================
# Home Assistant Add-on: RelayPrint Server
# Entrypoint for the add-on
# ==============================================================================

# Ensure user and group exist
addgroup --system lp
adduser --system lpadmin
adduser lpadmin lp

# Create required directories
mkdir -p /run/dbus

# Start dbus (required for CUPS and Avahi)
if ! pgrep -f dbus-daemon > /dev/null; then
    bashio::log.info "Starting dbus-daemon..."
    dbus-daemon --system --nofork &
fi

# Start the S6 supervisor
exec /init
