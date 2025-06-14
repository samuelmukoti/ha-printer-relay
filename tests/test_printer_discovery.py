#!/usr/bin/env python3
"""Unit tests for printer discovery functionality."""
import unittest
from unittest.mock import MagicMock, patch
import sys
import os

# Add the parent directory to the Python path so we can import the script
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'rootfs', 'usr', 'local', 'bin'))
import printer_discovery

class TestPrinterDiscovery(unittest.TestCase):
    """Test cases for printer discovery functionality."""

    def setUp(self):
        """Set up test fixtures."""
        self.mock_conn = MagicMock()
        with patch('cups.Connection', return_value=self.mock_conn):
            self.discovery = printer_discovery.PrinterDiscovery()

    def test_get_all_printers(self):
        """Test getting all printers."""
        # Mock printer data
        mock_printers = {
            'printer1': {'device-uri': 'usb://printer1'},
            'printer2': {'device-uri': 'ipp://printer2'}
        }
        self.mock_conn.getPrinters.return_value = mock_printers
        
        printers = self.discovery.get_all_printers()
        self.assertEqual(printers, mock_printers)
        self.mock_conn.getPrinters.assert_called_once()

    def test_get_printer_attributes(self):
        """Test getting printer attributes."""
        printer_name = 'test_printer'
        mock_attrs = {
            'printer-info': 'Test Printer',
            'printer-location': 'Office',
            'printer-make-and-model': 'Test Model',
            'printer-state': 3,
            'printer-state-message': 'Idle',
            'printer-is-shared': True,
            'device-uri': 'usb://test_printer',
            'document-format-supported': ['application/pdf']
        }
        self.mock_conn.getPrinterAttributes.return_value = mock_attrs
        
        attrs = self.discovery.get_printer_attributes(printer_name)
        self.assertEqual(attrs['name'], printer_name)
        self.assertEqual(attrs['info'], 'Test Printer')
        self.assertEqual(attrs['location'], 'Office')
        self.assertEqual(attrs['make_model'], 'Test Model')
        self.assertEqual(attrs['state'], 3)
        self.assertEqual(attrs['state_message'], 'Idle')
        self.assertEqual(attrs['is_shared'], True)
        self.assertEqual(attrs['uri'], 'usb://test_printer')
        self.assertEqual(attrs['supported_formats'], ['application/pdf'])

    def test_discover_network_printers(self):
        """Test network printer discovery."""
        mock_printers = {
            'network1': {'device-uri': 'ipp://printer1'},
            'network2': {'device-uri': 'socket://printer2'},
            'local1': {'device-uri': 'usb://printer3'}
        }
        self.mock_conn.getPrinters.return_value = mock_printers
        
        # Mock printer attributes
        def mock_get_attrs(name):
            return {
                'printer-info': f'Info for {name}',
                'printer-location': 'Network',
                'printer-make-and-model': 'Network Printer',
                'printer-state': 3,
                'printer-state-message': 'Idle',
                'printer-is-shared': True,
                'device-uri': mock_printers[name]['device-uri'],
                'document-format-supported': ['application/pdf']
            }
        
        self.mock_conn.getPrinterAttributes.side_effect = mock_get_attrs
        
        network_printers = self.discovery.discover_network_printers()
        self.assertEqual(len(network_printers), 2)
        uris = [p['uri'] for p in network_printers]
        self.assertIn('ipp://printer1', uris)
        self.assertIn('socket://printer2', uris)

    def test_discover_local_printers(self):
        """Test local printer discovery."""
        mock_printers = {
            'local1': {'device-uri': 'usb://printer1'},
            'local2': {'device-uri': 'parallel://printer2'},
            'network1': {'device-uri': 'ipp://printer3'}
        }
        self.mock_conn.getPrinters.return_value = mock_printers
        
        # Mock printer attributes
        def mock_get_attrs(name):
            return {
                'printer-info': f'Info for {name}',
                'printer-location': 'Local',
                'printer-make-and-model': 'Local Printer',
                'printer-state': 3,
                'printer-state-message': 'Idle',
                'printer-is-shared': False,
                'device-uri': mock_printers[name]['device-uri'],
                'document-format-supported': ['application/pdf']
            }
        
        self.mock_conn.getPrinterAttributes.side_effect = mock_get_attrs
        
        local_printers = self.discovery.discover_local_printers()
        self.assertEqual(len(local_printers), 2)
        uris = [p['uri'] for p in local_printers]
        self.assertIn('usb://printer1', uris)
        self.assertIn('parallel://printer2', uris)

    def test_get_default_printer(self):
        """Test getting default printer."""
        default_printer = 'default_printer'
        self.mock_conn.getDefault.return_value = default_printer
        
        result = self.discovery.get_default_printer()
        self.assertEqual(result, default_printer)
        self.mock_conn.getDefault.assert_called_once()

    def test_get_default_printer_none(self):
        """Test getting default printer when none is set."""
        self.mock_conn.getDefault.return_value = None
        
        result = self.discovery.get_default_printer()
        self.assertIsNone(result)
        self.mock_conn.getDefault.assert_called_once()

    def test_connection_error(self):
        """Test handling of CUPS connection error."""
        with patch('cups.Connection', side_effect=Exception("Connection failed")):
            with self.assertRaises(SystemExit):
                printer_discovery.PrinterDiscovery()

if __name__ == '__main__':
    unittest.main() 