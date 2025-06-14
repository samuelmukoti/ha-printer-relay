#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Check if mkcert is installed
if ! command -v mkcert &> /dev/null; then
    echo -e "${RED}mkcert is not installed. Installing...${NC}"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        brew install mkcert
        brew install nss # for Firefox
    else
        echo -e "${RED}Please install mkcert manually: https://github.com/FiloSottile/mkcert${NC}"
        exit 1
    fi
fi

# Check if python3 is installed
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}Python 3 is required but not installed.${NC}"
    exit 1
fi

# Create development directory
DEV_DIR=~/ha-addon-dev
ADDON_PATH=$DEV_DIR/addons/printer-relay
mkdir -p "$ADDON_PATH"

# Copy add-on files
echo -e "${YELLOW}Copying add-on files...${NC}"
cp -r rootfs "$ADDON_PATH/"
cp Dockerfile "$ADDON_PATH/"
cp config.yaml "$ADDON_PATH/"
cp run.sh "$ADDON_PATH/"

# Create certificates directory
CERT_DIR="$DEV_DIR/certs"
mkdir -p "$CERT_DIR"

# Generate certificates
echo -e "${YELLOW}Generating SSL certificates...${NC}"
cd "$CERT_DIR"
mkcert -install
mkcert localhost 127.0.0.1

# Create Python HTTPS server script
cat > "$DEV_DIR/serve.py" << 'EOF'
import http.server
import ssl
import os
from functools import partial

class CORSHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Methods', 'GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'X-Requested-With')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

port = 8099
directory = os.path.dirname(os.path.abspath(__file__))

handler = partial(CORSHTTPRequestHandler, directory=directory)
httpd = http.server.HTTPServer(('localhost', port), handler)

httpd.socket = ssl.wrap_socket(
    httpd.socket,
    certfile='certs/localhost+1.pem',
    keyfile='certs/localhost+1-key.pem',
    server_side=True
)

print(f"Serving HTTPS on localhost:{port}")
httpd.serve_forever()
EOF

# Get local IP address
if [[ "$OSTYPE" == "darwin"* ]]; then
    IP=$(ipconfig getifaddr en0 || ipconfig getifaddr en1)
else
    IP=$(hostname -I | awk '{print $1}')
fi

echo -e "${GREEN}Setup complete!${NC}"
echo
echo "To start the HTTPS server:"
echo "1. cd $DEV_DIR"
echo "2. python3 serve.py"
echo
echo "Add this URL to Home Assistant:"
echo -e "${YELLOW}https://localhost:8099${NC}"
echo
echo "If Home Assistant is on a different machine, you'll need to:"
echo "1. Modify the serve.py script to use $IP instead of localhost"
echo "2. Add the certificate to Home Assistant's trusted certificates"
echo "3. Use https://$IP:8099 as the repository URL" 