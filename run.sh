#!/bin/bash
# Minimal entry point for RelayPrint Server addon
service dbus start
service avahi-daemon start
service cups start
# Placeholder for API server or print relay logic
tail -f /dev/null
