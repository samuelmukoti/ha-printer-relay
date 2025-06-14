#!/usr/bin/env python3
"""
Printer discovery script for RelayPrint.
Discovers and manages local and network printers using CUPS.
"""
import cups
import json
import logging
import sys
from typing import Dict, List, Optional

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('printer_discovery')

class PrinterDiscovery:
    """Handles printer discovery and management via CUPS."""
    
    def __init__(self):
        """Initialize CUPS connection."""
        try:
            self.conn = cups.Connection()
            logger.info("Successfully connected to CUPS server")
        except Exception as e:
            logger.error(f"Failed to connect to CUPS server: {e}")
            sys.exit(1)

    def get_all_printers(self) -> Dict[str, Dict]:
        """Get all available printers and their attributes."""
        try:
            printers = self.conn.getPrinters()
            logger.info(f"Found {len(printers)} printer(s)")
            return printers
        except Exception as e:
            logger.error(f"Failed to get printers: {e}")
            return {}

    def get_printer_attributes(self, printer_name: str) -> Dict:
        """Get detailed attributes for a specific printer."""
        try:
            attrs = self.conn.getPrinterAttributes(printer_name)
            return {
                "name": printer_name,
                "info": attrs.get("printer-info", ""),
                "location": attrs.get("printer-location", ""),
                "make_model": attrs.get("printer-make-and-model", ""),
                "state": attrs.get("printer-state", 0),
                "state_message": attrs.get("printer-state-message", ""),
                "is_shared": attrs.get("printer-is-shared", False),
                "uri": attrs.get("device-uri", ""),
                "supported_formats": attrs.get("document-format-supported", [])
            }
        except Exception as e:
            logger.error(f"Failed to get attributes for printer {printer_name}: {e}")
            return {}

    def discover_network_printers(self) -> List[Dict]:
        """Discover network printers using CUPS browsing."""
        try:
            # Get list of network-connected printers
            printers = self.get_all_printers()
            network_printers = []
            
            for name, data in printers.items():
                if data.get('device-uri', '').startswith(('socket:', 'ipp:', 'ipps:', 'http:', 'https:')):
                    attrs = self.get_printer_attributes(name)
                    if attrs:
                        network_printers.append(attrs)
            
            logger.info(f"Discovered {len(network_printers)} network printer(s)")
            return network_printers
        except Exception as e:
            logger.error(f"Failed to discover network printers: {e}")
            return []

    def discover_local_printers(self) -> List[Dict]:
        """Discover locally connected printers (USB, parallel, etc.)."""
        try:
            printers = self.get_all_printers()
            local_printers = []
            
            for name, data in printers.items():
                if data.get('device-uri', '').startswith(('usb:', 'parallel:', 'serial:')):
                    attrs = self.get_printer_attributes(name)
                    if attrs:
                        local_printers.append(attrs)
            
            logger.info(f"Discovered {len(local_printers)} local printer(s)")
            return local_printers
        except Exception as e:
            logger.error(f"Failed to discover local printers: {e}")
            return []

    def get_default_printer(self) -> Optional[str]:
        """Get the default printer name."""
        try:
            default = self.conn.getDefault()
            if default:
                logger.info(f"Default printer: {default}")
            else:
                logger.info("No default printer set")
            return default
        except Exception as e:
            logger.error(f"Failed to get default printer: {e}")
            return None

def main():
    """Main function to demonstrate printer discovery."""
    discovery = PrinterDiscovery()
    
    # Get all printers
    all_printers = discovery.get_all_printers()
    
    # Get network and local printers
    network_printers = discovery.discover_network_printers()
    local_printers = discovery.discover_local_printers()
    
    # Get default printer
    default_printer = discovery.get_default_printer()
    
    # Prepare output
    result = {
        "default_printer": default_printer,
        "network_printers": network_printers,
        "local_printers": local_printers,
        "total_printers": len(all_printers)
    }
    
    # Output as JSON
    print(json.dumps(result, indent=2))

if __name__ == "__main__":
    main() 