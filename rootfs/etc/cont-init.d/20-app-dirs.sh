#!/command/with-contenv bashio
# shellcheck shell=bash
set -euo pipefail

bashio::log.info "Setting up application directories..."

# ==============================================================================
# Configure persistent storage for application data
# ==============================================================================
mkdir -p \
    /data/print_jobs \
    /data/api

chmod -R 755 /data/print_jobs
chmod -R 755 /data/api

bashio::log.info "Application directories setup complete"
