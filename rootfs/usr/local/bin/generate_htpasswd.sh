#!/bin/bash

# Check if username and password are provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <username> <password>"
    exit 1
fi

USERNAME=$1
PASSWORD=$2
HTPASSWD_FILE="/etc/nginx/.htpasswd"

# Install apache2-utils if htpasswd is not available
if ! command -v htpasswd &> /dev/null; then
    apk add --no-cache apache2-utils
fi

# Generate .htpasswd file
htpasswd -bc "$HTPASSWD_FILE" "$USERNAME" "$PASSWORD"

# Set proper permissions
chmod 644 "$HTPASSWD_FILE"

echo "Generated .htpasswd file with provided credentials" 