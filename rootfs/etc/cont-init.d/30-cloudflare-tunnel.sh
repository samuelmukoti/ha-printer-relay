#!/usr/bin/with-contenv bashio
# ==============================================================================
# Cloudflare Tunnel Setup
# 
# Configures cloudflared to create a secure tunnel for remote mobile app access.
# User must create a tunnel in Cloudflare Zero Trust dashboard and provide the token.
# ==============================================================================

set -e

CONFIG_PATH=/data/options.json
TUNNEL_CONFIG_DIR=/data/cloudflare

# Check if Cloudflare tunnel is enabled
if bashio::config.true 'cloudflare.enabled'; then
    bashio::log.info "Cloudflare Tunnel is enabled"
    
    # Get the tunnel token from config
    TUNNEL_TOKEN=$(bashio::config 'cloudflare.tunnel_token')
    
    if [ -z "$TUNNEL_TOKEN" ]; then
        bashio::log.warning "Cloudflare Tunnel enabled but no token provided!"
        bashio::log.warning "Please configure the tunnel token in addon settings."
        bashio::log.info ""
        bashio::log.info "To set up Cloudflare Tunnel:"
        bashio::log.info "1. Go to https://one.dash.cloudflare.com/"
        bashio::log.info "2. Navigate to Access → Tunnels"
        bashio::log.info "3. Create a new tunnel named 'relayprint'"
        bashio::log.info "4. In Public Hostname, add:"
        bashio::log.info "   - Subdomain: relayprint (or your choice)"
        bashio::log.info "   - Domain: yourdomain.com"
        bashio::log.info "   - Service: HTTP://localhost:7779"
        bashio::log.info "5. Copy the tunnel token and paste in addon config"
        bashio::log.info ""
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

