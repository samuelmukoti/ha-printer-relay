#!/usr/bin/with-contenv bashio
# ==============================================================================
# Cloudflare Tunnel Setup
# 
# Supports two modes:
# 1. Quick Tunnel (default): Zero config, auto-generated URL - no account needed!
# 2. Named Tunnel: Custom domain with Cloudflare account and token
#
# Configuration can come from:
# - Dashboard UI (saved to /data/cloudflare_config.json) - takes precedence
# - Addon options (config.yaml / options.json)
# ==============================================================================

set -e

TUNNEL_CONFIG_DIR=/data/cloudflare
DASHBOARD_CONFIG=/data/cloudflare_config.json

# Initialize variables
TUNNEL_ENABLED=false
TUNNEL_TOKEN=""
TUNNEL_MODE="quick"

# Check dashboard config first (takes precedence)
if [ -f "$DASHBOARD_CONFIG" ]; then
    bashio::log.info "Reading Cloudflare config from dashboard settings..."
    
    # Parse JSON config using jq (available in HA base image)
    if command -v jq &> /dev/null; then
        TUNNEL_ENABLED=$(jq -r '.enabled // false' "$DASHBOARD_CONFIG")
        TUNNEL_TOKEN=$(jq -r '.tunnel_token // ""' "$DASHBOARD_CONFIG")
        TUNNEL_MODE=$(jq -r '.mode // "quick"' "$DASHBOARD_CONFIG")
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
        TUNNEL_TOKEN=$(bashio::config 'cloudflare.tunnel_token' || echo "")
    fi
fi

# Auto-detect mode based on token
if [ -n "$TUNNEL_TOKEN" ] && [ "$TUNNEL_TOKEN" != "null" ] && [ "$TUNNEL_TOKEN" != "" ]; then
    TUNNEL_MODE="named"
else
    TUNNEL_MODE="quick"
fi

# Create config directory
mkdir -p "$TUNNEL_CONFIG_DIR"

# Process configuration
if [ "$TUNNEL_ENABLED" = "true" ]; then
    bashio::log.info "Cloudflare Tunnel is enabled (mode: $TUNNEL_MODE)"
    
    if [ "$TUNNEL_MODE" = "named" ]; then
        # Named Tunnel mode - requires token
        bashio::log.info "Using Named Tunnel with custom domain"
        
        # Store the token for the service to use
        echo "$TUNNEL_TOKEN" > "$TUNNEL_CONFIG_DIR/tunnel_token"
        chmod 600 "$TUNNEL_CONFIG_DIR/tunnel_token"
    else
        # Quick Tunnel mode - no token needed!
        bashio::log.info "Using Quick Tunnel (zero configuration mode)"
        bashio::log.info "A temporary public URL will be auto-generated"
        
        # Remove any existing token file for quick mode
        rm -f "$TUNNEL_CONFIG_DIR/tunnel_token" 2>/dev/null || true
    fi
    
    # Create a marker file to indicate tunnel is enabled
    touch "$TUNNEL_CONFIG_DIR/enabled"
    
    bashio::log.info "Cloudflare Tunnel configured successfully"
    bashio::log.info "The tunnel will start with the cloudflared service"
else
    bashio::log.info "Cloudflare Tunnel is disabled"
    # Remove enabled marker and token if they exist
    rm -f "$TUNNEL_CONFIG_DIR/enabled" 2>/dev/null || true
    rm -f "$TUNNEL_CONFIG_DIR/tunnel_url.txt" 2>/dev/null || true
fi

