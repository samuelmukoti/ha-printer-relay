# Mobile App Authentication Guide

This document explains how the RelayPrint mobile app authenticates with the Home Assistant addon for remote printing.

## TL;DR

- **Ingress does NOT work** for REST API calls from mobile apps (session-cookie only)
- **Use direct port access** (7779) with OAuth or Long-Lived Access Tokens (LLAT)
- **For remote access:** Enable built-in **Cloudflare Tunnel** in addon settings (recommended)
- Alternative: port forwarding or VPN

---

## Understanding HA Addon Authentication

### Ingress (Browser Only)

Home Assistant Ingress is designed for **web UI access only**. It works via:

1. User logs into HA in browser
2. Session cookies are created
3. Ingress proxies requests using those cookies
4. The `/api/hassio/ingress/*` and `/api/hassio_ingress/*` endpoints **require Supervisor access** which OAuth/LLAT tokens do NOT have

| Endpoint | Token Type | Result |
|----------|------------|--------|
| `/api/config` | LLAT ✅ | Works |
| `/api/hassio/ingress/session` | LLAT | ❌ 401 Unauthorized |
| `/api/hassio/addons` | LLAT | ❌ 401 Unauthorized |
| `/{slug}/ingress/api/*` | LLAT | ❌ Returns HTML, not JSON |

**Conclusion:** Ingress cannot be used for mobile app API access.

### Direct Port Access (Mobile Apps)

The addon exposes port **7779** for direct REST API access:

```
http://your-ha-ip:7779/api/health
http://your-ha-ip:7779/api/printers
http://your-ha-ip:7779/api/print
```

Authentication: **Bearer token in Authorization header**

```bash
curl -X GET "http://your-ha-ip:7779/api/printers" \
  -H "Authorization: Bearer YOUR_LLAT_TOKEN"
```

The addon validates tokens by calling HA's internal API.

---

## Remote Access Setup

### Option 1: Built-in Cloudflare Tunnel (Recommended)

The addon has built-in Cloudflare Tunnel support for easy remote access:

#### Setup Steps:

1. **Create a Cloudflare account** (free) at https://dash.cloudflare.com
2. **Add your domain** to Cloudflare (or use a subdomain of an existing domain)
3. **Go to Zero Trust Dashboard:** https://one.dash.cloudflare.com
4. **Create a Tunnel:**
   - Navigate to: Access → Tunnels → Create a tunnel
   - Name: `relayprint` (or any name you prefer)
   - Save the tunnel
5. **Configure Public Hostname:**
   - Subdomain: `relayprint` (or your choice)
   - Domain: Select your domain
   - Service Type: `HTTP`
   - Service URL: `localhost:7779`
6. **Copy the Tunnel Token:**
   - In the tunnel configuration, find "Install and run a connector"
   - Copy the token (starts with `eyJ...`)
7. **Configure the Addon:**
   - Go to Home Assistant → Settings → Add-ons → RelayPrint → Configuration
   - Enable `cloudflare.enabled`
   - Paste the token in `cloudflare.tunnel_token`
   - Enter your tunnel URL in `cloudflare.tunnel_url` (e.g., `https://relayprint.yourdomain.com`)
   - Restart the addon

#### How it Works:

```
Mobile App → Cloudflare Edge → Tunnel → Addon (port 7779)
                                ↑
                    Outbound connection from addon
                    (no port forwarding needed!)
```

The addon establishes an outbound connection to Cloudflare, so no port forwarding or firewall changes are required.

### Option 2: Same Network (WiFi)

When on the same WiFi as your Home Assistant:

1. Use local HA IP: `http://192.168.x.x:7779`
2. No additional setup required
3. Works with LLAT or OAuth tokens

### Option 3: Router Port Forwarding

1. Forward external port (e.g., 7779) to your HA server's port 7779
2. Use DDNS if you don't have a static IP
3. Access via: `http://your-public-ip:7779/api/printers`

⚠️ **Security Note:** When exposing port 7779 directly, ensure you use HTTPS (via reverse proxy) and valid authentication tokens.

### Option 4: VPN

Connect to your home network via VPN (WireGuard, OpenVPN), then use local IP access.

---

## Mobile App Implementation

### Discovery Flow

```
1. User enters HA URL (e.g., https://your-ha.duckdns.org)
2. App performs OAuth login to get access token
3. App tries to discover addon:
   a. Try direct port: {ha-url}:7779/api/health
   b. If fails, prompt user to set up remote access
4. Store working URL for future API calls
```

### Token Types

| Token Type | How to Get | Use Case |
|------------|------------|----------|
| **OAuth Token** | WebView login flow | Short-lived, auto-refresh |
| **Long-Lived Access Token (LLAT)** | HA Profile → Security | Long-lived, manual entry |

For mobile apps, **OAuth is preferred** for better UX.

### API Endpoints

```
GET  /api/health              - Health check (no auth required)
GET  /api/printers            - List configured printers
POST /api/printers/add        - Add a new printer
POST /api/print               - Submit print job (multipart form)
GET  /api/print/{id}/status   - Get job status
GET  /api/discover            - Discover network printers
```

---

## Troubleshooting

### "Connection Timeout" on port 7779

- Port 7779 is not accessible from your network
- Solutions:
  - Use local WiFi instead of mobile data
  - Set up Cloudflare Tunnel or port forwarding

### "401 Unauthorized"

- Token is invalid or expired
- Ensure you're using a valid LLAT or OAuth token
- Check token hasn't been revoked in HA

### "Got HTML instead of JSON"

- You're hitting ingress URL, not direct port
- Ingress returns HA frontend, not addon API
- Use port 7779 directly

---

## Security Considerations

1. **Always use HTTPS** for remote access (reverse proxy or tunnel)
2. **Rotate tokens** periodically
3. **Use OAuth** for mobile apps (auto-expiry)
4. **Limit port exposure** - only forward what you need
5. **Monitor access logs** in HA

---

## Summary

```
┌─────────────────────────────────────────────────────────────┐
│                    ACCESS METHODS                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  BROWSER (Web UI)                                           │
│  └── Via HA Ingress ✅                                      │
│      └── HA handles auth via session cookies                │
│                                                             │
│  MOBILE APP (REST API)                                      │
│  ├── Local: Direct Port 7779 ✅                             │
│  │   └── Bearer token authentication                        │
│  │                                                          │
│  └── Remote: Cloudflare Tunnel ✅ (Recommended)             │
│      └── Enable in addon settings                           │
│      └── No port forwarding needed                          │
│      └── Bearer token authentication                        │
│                                                             │
│  ❌ HA Ingress API does NOT work with tokens                │
│     (Requires Supervisor access that tokens don't have)     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

