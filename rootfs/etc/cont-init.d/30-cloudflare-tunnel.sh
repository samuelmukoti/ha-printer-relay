#!/usr/bin/with-contenv bashio
# ==============================================================================
# Cloudflare Tunnel Setup
# 
# Configures cloudflared to create a secure tunnel for remote mobile app access.
# Configuration can come from:
# 1. Dashboard UI (saved to /data/cloudflare_config.json) - takes precedence
# 2. Addon options (config.yaml / options.json)
# ==============================================================================

set -e

TUNNEL_CONFIG_DIR=/data/cloudflare
DASHBOARD_CONFIG=/data/cloudflare_config.json

# Initialize variables
TUNNEL_ENABLED=false
TUNNEL_TOKEN=""

# Check dashboard config first (takes precedence)
if [ -f "$DASHBOARD_CONFIG" ]; then
    bashio::log.info "Reading Cloudflare config from dashboard settings..."
    
    # Parse JSON config using jq (available in HA base image)
    if command -v jq &> /dev/null; then
        TUNNEL_ENABLED=$(jq -r '.enabled // false' "$DASHBOARD_CONFIG")
        TUNNEL_TOKEN=$(jq -r '.tunnel_token // ""' "$DASHBOARD_CONFIG")
    else
        # Fallback: basic parsing
        if grep -q '"enabled": true' "$DASHBOARD_CONFIG" 2>/dev/null; then
            TUNNEL_ENABLED=true
        fi
        TUNNEL_TOKEN=$(grep -o '"tunnel_token": "[^"]*"' "$DASHBOARD_CONFIG" 2>/dev/null | cut -d'"' -f4 || true)
    fi
fi

# Fallback to addon options if dashboard config doesn't have tunnel enabled
if [ "$TUNNEL_ENABLED" != "true" ]; then
    if bashio::config.true 'cloudflare.enabled'; then
        bashio::log.info "Reading Cloudflare config from addon options..."
        TUNNEL_ENABLED=true
        TUNNEL_TOKEN=$(bashio::config 'cloudflare.tunnel_token')
    fi
fi

# Process configuration
if [ "$TUNNEL_ENABLED" = "true" ]; then
    bashio::log.info "Cloudflare Tunnel is enabled"
    
    if [ -z "$TUNNEL_TOKEN" ]; then
        bashio::log.warning "Cloudflare Tunnel enabled but no token provided!"
        bashio::log.warning "Configure via: Dashboard UI (Settings tab) or addon Configuration"
        bashio::log.info ""
        bashio::log.info "To set up Cloudflare Tunnel:"
        bashio::log.info "1. Go to https://one.dash.cloudflare.com/"
        bashio::log.info "2. Navigate to Access → Tunnels"
        bashio::log.info "3. Create a new tunnel named 'relayprint'"
        bashio::log.info "4. In Public Hostname, add:"
        bashio::log.info "   - Subdomain: relayprint (or your choice)"
        bashio::log.info "   - Domain: yourdomain.com"
        bashio::log.info "   - Service: HTTP://localhost:7779"
        bashio::log.info "5. Copy the tunnel token and paste in Settings"
        bashio::log.info ""
        # Remove enabled marker since token is missing
        rm -f "$TUNNEL_CONFIG_DIR/enabled" 2>/dev/null || true
        exit 0
    fi
    
    # Create config directory
    mkdir -p "$TUNNEL_CONFIG_DIR"
    
    # Store the token for the service to use
    echo "$TUNNEL_TOKEN" > "$TUNNEL_CONFIG_DIR/tunnel_token"
    chmod 600 "$TUNNEL_CONFIG_DIR/tunnel_token"
    
    # Create a marker file to indicate tunnel is configured
    touch "$TUNNEL_CONFIG_DIR/enabled"
    
    bashio::log.info "Cloudflare Tunnel configured successfully"
    bashio::log.info "The tunnel will start with the cloudflared service"
else
    bashio::log.info "Cloudflare Tunnel is disabled"
    # Remove enabled marker if it exists
    rm -f "$TUNNEL_CONFIG_DIR/enabled" 2>/dev/null || true
fi

