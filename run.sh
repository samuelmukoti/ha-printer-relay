#!/usr/bin/env bash
set -e

# Start CUPS service
echo "Starting CUPS service..."
cupsd -f &
CUPSD_PID=$!

# Start Avahi daemon
echo "Starting Avahi daemon..."
avahi-daemon --daemonize

# Start the print API server
echo "Starting Print API server..."
python3 /usr/local/bin/print_api.py &
API_PID=$!

# Wait for services to be ready
sleep 2

# Check if services are running
if ! ps -p $CUPSD_PID > /dev/null; then
    echo "CUPS failed to start"
    exit 1
fi

if ! ps -p $API_PID > /dev/null; then
    echo "API server failed to start"
    exit 1
fi

if ! pgrep avahi-daemon > /dev/null; then
    echo "Avahi daemon failed to start"
    exit 1
fi

echo "All services started successfully"

# Keep the container running
wait $CUPSD_PID $API_PID
