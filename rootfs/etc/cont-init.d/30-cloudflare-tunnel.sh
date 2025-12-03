#!/usr/bin/with-contenv bashio
# ==============================================================================
# Tunnel Setup
#
# Supports three providers:
# 1. LocalTunnel (default): Simple, reliable, no rate limits
# 2. Cloudflare Quick Tunnel: Zero config, auto-generated URL
# 3. Cloudflare Named Tunnel: Custom domain with Cloudflare account
#
# Configuration can come from:
# - Dashboard UI (saved to /data/tunnel_config.json) - takes precedence
# - Addon options (config.yaml / options.json)
# ==============================================================================

set -e

TUNNEL_CONFIG_DIR=/data/tunnel
DASHBOARD_CONFIG=/data/tunnel_config.json

# Initialize variables
TUNNEL_ENABLED=false
TUNNEL_PROVIDER="localtunnel"
TUNNEL_TOKEN=""

# Check dashboard config first (takes precedence)
if [ -f "$DASHBOARD_CONFIG" ]; then
    bashio::log.info "Reading tunnel config from dashboard settings..."

    # Parse JSON config using jq (available in HA base image)
    if command -v jq &> /dev/null; then
        TUNNEL_ENABLED=$(jq -r '.enabled // false' "$DASHBOARD_CONFIG")
        TUNNEL_PROVIDER=$(jq -r '.provider // "localtunnel"' "$DASHBOARD_CONFIG")
        TUNNEL_TOKEN=$(jq -r '.tunnel_token // ""' "$DASHBOARD_CONFIG")
    else
        # Fallback: basic parsing
        if grep -q '"enabled": true' "$DASHBOARD_CONFIG" 2>/dev/null; then
            TUNNEL_ENABLED=true
        fi
        TUNNEL_PROVIDER=$(grep -o '"provider": "[^"]*"' "$DASHBOARD_CONFIG" 2>/dev/null | cut -d'"' -f4 || echo "localtunnel")
        TUNNEL_TOKEN=$(grep -o '"tunnel_token": "[^"]*"' "$DASHBOARD_CONFIG" 2>/dev/null | cut -d'"' -f4 || true)
    fi
fi

# Fallback to addon options if dashboard config doesn't have tunnel enabled
if [ "$TUNNEL_ENABLED" != "true" ]; then
    if bashio::config.true 'tunnel.enabled'; then
        bashio::log.info "Reading tunnel config from addon options..."
        TUNNEL_ENABLED=true
        TUNNEL_PROVIDER=$(bashio::config 'tunnel.provider' || echo "localtunnel")
        TUNNEL_TOKEN=$(bashio::config 'tunnel.tunnel_token' || echo "")
    fi
fi

# Validate provider (default to localtunnel if invalid)
case "$TUNNEL_PROVIDER" in
    "localtunnel"|"cloudflare_quick"|"cloudflare_named")
        # Valid provider
        ;;
    *)
        bashio::log.warning "Unknown provider '$TUNNEL_PROVIDER', defaulting to localtunnel"
        TUNNEL_PROVIDER="localtunnel"
        ;;
esac

# Create config directory
mkdir -p "$TUNNEL_CONFIG_DIR"

# Store provider for the service to use
echo "$TUNNEL_PROVIDER" > "$TUNNEL_CONFIG_DIR/provider"

# Process configuration
if [ "$TUNNEL_ENABLED" = "true" ]; then
    bashio::log.info "Tunnel is enabled (provider: $TUNNEL_PROVIDER)"

    case "$TUNNEL_PROVIDER" in
        "localtunnel")
            bashio::log.info "Using LocalTunnel - simple and reliable"
            bashio::log.info "A public URL will be generated at *.loca.lt"
            rm -f "$TUNNEL_CONFIG_DIR/tunnel_token" 2>/dev/null || true
            ;;
        "cloudflare_quick")
            bashio::log.info "Using Cloudflare Quick Tunnel (zero configuration mode)"
            bashio::log.info "A temporary URL will be generated at *.trycloudflare.com"
            rm -f "$TUNNEL_CONFIG_DIR/tunnel_token" 2>/dev/null || true
            ;;
        "cloudflare_named")
            bashio::log.info "Using Cloudflare Named Tunnel with custom domain"
            if [ -n "$TUNNEL_TOKEN" ] && [ "$TUNNEL_TOKEN" != "null" ] && [ "$TUNNEL_TOKEN" != "" ]; then
                echo "$TUNNEL_TOKEN" > "$TUNNEL_CONFIG_DIR/tunnel_token"
                chmod 600 "$TUNNEL_CONFIG_DIR/tunnel_token"
            else
                bashio::log.warning "Named tunnel requires a token - falling back to quick tunnel"
                echo "cloudflare_quick" > "$TUNNEL_CONFIG_DIR/provider"
            fi
            ;;
    esac

    # Create a marker file to indicate tunnel is enabled
    touch "$TUNNEL_CONFIG_DIR/enabled"

    bashio::log.info "Tunnel configured successfully"
    bashio::log.info "The tunnel will start with the tunnel service"
else
    bashio::log.info "Tunnel is disabled"
    # Remove enabled marker and URL if they exist
    rm -f "$TUNNEL_CONFIG_DIR/enabled" 2>/dev/null || true
    rm -f "$TUNNEL_CONFIG_DIR/tunnel_url.txt" 2>/dev/null || true
fi

