#!/usr/bin/env bash
set -e

echo "=========================================="
echo "  RelayPrint - Home Assistant Printer Relay"
echo "=========================================="

# Start D-Bus (required for Avahi)
echo "Starting D-Bus..."
mkdir -p /run/dbus
dbus-daemon --system --nofork &
DBUS_PID=$!
sleep 1

# Start CUPS service
echo "Starting CUPS service..."
cupsd -f &
CUPSD_PID=$!

# Start Avahi daemon
echo "Starting Avahi daemon..."
avahi-daemon --daemonize

# Start the print API server
# Port 7779 chosen to avoid conflicts with common services
echo "Starting Print API server on port 7779..."
cd /usr/local/bin
python3 -c "
from print_api import app
app.run(host='0.0.0.0', port=7779, threaded=True)
" &
API_PID=$!

# Wait for services to be ready
sleep 2

# Check if services are running
if ! ps -p $CUPSD_PID > /dev/null; then
    echo "ERROR: CUPS failed to start"
    exit 1
fi

if ! ps -p $API_PID > /dev/null; then
    echo "ERROR: API server failed to start"
    exit 1
fi

if ! pgrep avahi-daemon > /dev/null; then
    echo "ERROR: Avahi daemon failed to start"
    exit 1
fi

echo "=========================================="
echo "  All services started successfully!"
echo "  API available on port 7779 (via HA Ingress)"
echo "  CUPS available on port 631"
echo "=========================================="

# Keep the container running
wait $CUPSD_PID $API_PID
