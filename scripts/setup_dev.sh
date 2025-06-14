#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Define paths
ADDON_PATH=~/ha-addon-dev/addons/printer-relay
SOURCE_PATH=$(pwd)

echo -e "${YELLOW}Setting up development environment...${NC}"

# Create development directory structure
mkdir -p "$ADDON_PATH"

# Copy necessary files
echo -e "${YELLOW}Copying add-on files...${NC}"
cp -r "$SOURCE_PATH/rootfs" "$ADDON_PATH/"
cp "$SOURCE_PATH/Dockerfile" "$ADDON_PATH/"
cp "$SOURCE_PATH/config.yaml" "$ADDON_PATH/"
cp "$SOURCE_PATH/run.sh" "$ADDON_PATH/"

echo -e "${GREEN}Development environment setup complete!${NC}"
echo -e "${YELLOW}Add-on files copied to: ${NC}$ADDON_PATH"
echo
echo "To test the add-on in Home Assistant:"
echo "1. In Home Assistant, go to Settings > Add-ons > Add-on Store"
echo "2. Click the menu (⋮) and select 'Repositories'"
echo "3. Add this URL: $ADDON_PATH"
echo "4. The Printer Relay add-on should now appear in the store"
echo "5. Install and configure the add-on" 