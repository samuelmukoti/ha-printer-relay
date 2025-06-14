#!/usr/bin/env python3
"""Unit tests for RelayPrint Server configuration validation."""
import json
import os
import unittest
import yaml
from typing import Dict, Any

class TestConfigValidation(unittest.TestCase):
    """Test cases for configuration validation."""

    def setUp(self):
        """Load configuration files before tests."""
        # Load config.json
        with open('config.json', 'r') as f:
            self.config_json = json.load(f)
        
        # Load config.yaml
        with open('config.yaml', 'r') as f:
            self.config_yaml = yaml.safe_load(f)

    def test_basic_config_structure(self):
        """Test basic configuration structure."""
        required_fields = [
            'name', 'version', 'slug', 'description', 'arch',
            'ports', 'options', 'schema'
        ]
        for field in required_fields:
            self.assertIn(field, self.config_json)

    def test_architecture_support(self):
        """Test architecture configuration."""
        required_arch = ['aarch64', 'amd64', 'armhf', 'armv7', 'i386']
        self.assertTrue(all(arch in self.config_json['arch'] for arch in required_arch))

    def test_port_configuration(self):
        """Test port configuration."""
        required_ports = {
            '631/tcp': 631,  # CUPS
            '5353/udp': 5353  # Avahi
        }
        self.assertEqual(self.config_json['ports'], required_ports)

    def test_options_schema_match(self):
        """Test that options match their schema definitions."""
        options = self.config_json['options']
        schema = self.config_json['schema']
        
        def validate_against_schema(options: Dict[str, Any], schema: Dict[str, Any], path: str = '') -> bool:
            for key, value in options.items():
                schema_key = f"{path}{key}" if path else key
                self.assertIn(key, schema, f"Option {schema_key} not found in schema")
                
                if isinstance(value, dict):
                    self.assertTrue(isinstance(schema[key], dict),
                                 f"Schema mismatch for {schema_key}")
                    validate_against_schema(value, schema[key], f"{key}.")
                elif isinstance(value, list):
                    self.assertTrue(isinstance(schema[key], list) or 
                                 schema[key].startswith('list('),
                                 f"Schema mismatch for {schema_key}")
            return True

        self.assertTrue(validate_against_schema(options, schema))

    def test_ssl_configuration(self):
        """Test SSL configuration options."""
        self.assertIn('ssl', self.config_json['options'])
        self.assertIn('certfile', self.config_json['options'])
        self.assertIn('keyfile', self.config_json['options'])
        
        self.assertEqual(self.config_json['schema']['ssl'], 'bool')
        self.assertEqual(self.config_json['schema']['certfile'], 'str')
        self.assertEqual(self.config_json['schema']['keyfile'], 'str')

    def test_printer_options(self):
        """Test printer-specific options."""
        options = self.config_json['options']['printer_options']
        schema = self.config_json['schema']['printer_options']
        
        required_options = ['default_media', 'color_mode', 'duplex']
        for option in required_options:
            self.assertIn(option, options)
            self.assertIn(option, schema)

    def test_advanced_options(self):
        """Test advanced configuration options."""
        options = self.config_json['options']['advanced']
        schema = self.config_json['schema']['advanced']
        
        # Test numeric ranges
        self.assertTrue(schema['cups_timeout'].startswith('int('))
        self.assertTrue(schema['job_retention'].startswith('int('))
        self.assertTrue(schema['max_jobs'].startswith('int('))

    def test_security_options(self):
        """Test security-related configuration."""
        self.assertIn('remote_access', self.config_json['options'])
        self.assertIn('allowed_interfaces', self.config_json['options'])
        
        self.assertEqual(self.config_json['schema']['remote_access'], 'bool')
        self.assertTrue(isinstance(self.config_json['schema']['allowed_interfaces'], list))

    def test_yaml_ui_configuration(self):
        """Test UI configuration in config.yaml."""
        self.assertEqual(self.config_yaml['name'], self.config_json['name'])
        self.assertEqual(self.config_yaml['version'], self.config_json['version'])
        self.assertTrue('ingress' in self.config_yaml)
        self.assertTrue('panel_icon' in self.config_yaml)

if __name__ == '__main__':
    unittest.main() 