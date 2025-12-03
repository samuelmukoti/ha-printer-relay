#!/command/with-contenv bashio
# shellcheck shell=bash
set -euo pipefail

bashio::log.info "Setting up application directories..."

# ==============================================================================
# Configure persistent storage for application data
# ==============================================================================
mkdir -p \
    /data/print_jobs \
    /data/api \
    /data/tunnel

chmod -R 755 /data/print_jobs
chmod -R 755 /data/api
chmod -R 755 /data/tunnel

# Migrate old cloudflare config to new tunnel config if exists
if [ -f /data/cloudflare_config.json ] && [ ! -f /data/tunnel_config.json ]; then
    bashio::log.info "Migrating cloudflare config to tunnel config..."
    cp /data/cloudflare_config.json /data/tunnel_config.json
fi

bashio::log.info "Application directories setup complete"
