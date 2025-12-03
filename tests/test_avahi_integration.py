#!/usr/bin/env python3
import unittest
import subprocess
import time
import socket
import dbus
import avahi
from dbus.mainloop.glib import DBusGMainLoop
from gi.repository import GLib

class TestAvahiIntegration(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        """Start Avahi daemon and wait for it to be ready."""
        # Initialize D-Bus main loop
        DBusGMainLoop(set_as_default=True)
        cls.main_loop = GLib.MainLoop()

        # Start Avahi daemon with test configuration
        cls.avahi_process = subprocess.Popen([
            "avahi-daemon",
            "--config", "tests/test_data/avahi-test.conf",
            "-f"
        ])
        time.sleep(2)  # Wait for Avahi to start

        # Connect to D-Bus
        cls.bus = dbus.SystemBus()
        cls.server = dbus.Interface(
            cls.bus.get_object(avahi.DBUS_NAME, avahi.DBUS_PATH_SERVER),
            avahi.DBUS_INTERFACE_SERVER
        )

    @classmethod
    def tearDownClass(cls):
        """Stop Avahi daemon and cleanup."""
        if cls.avahi_process:
            cls.avahi_process.terminate()
            cls.avahi_process.wait()

    def setUp(self):
        """Setup test environment."""
        self.printer_name = "test_printer"
        self.printer_type = "_ipp._tcp"
        self.printer_port = 631

    def test_01_publish_service(self):
        """Test publishing a printer service."""
        # Create a new service entry group
        group = dbus.Interface(
            self.bus.get_object(avahi.DBUS_NAME, self.server.EntryGroupNew()),
            avahi.DBUS_INTERFACE_ENTRY_GROUP
        )

        # Add printer service
        group.AddService(
            avahi.IF_UNSPEC,    # interface
            avahi.PROTO_UNSPEC, # protocol
            dbus.UInt32(0),     # flags
            self.printer_name,   # name
            self.printer_type,   # service type
            "",                 # domain
            "",                 # host
            dbus.UInt16(self.printer_port),  # port
            avahi.string_array_to_txt_array([  # TXT record
                "rp=printers/test_printer",
                "ty=RelayPrint Test",
                "adminurl=http://localhost:631/printers/test_printer",
                "note=Test Printer",
                "priority=50",
                "product=(RelayPrint)",
                "pdl=application/pdf,application/postscript",
                "Color=T",
                "Duplex=T",
                "usb_MFG=RelayPrint",
                "usb_MDL=Test",
            ])
        )

        # Commit the service
        group.Commit()
        time.sleep(1)  # Wait for service to be published

        # Verify service is published
        browser = dbus.Interface(
            self.bus.get_object(avahi.DBUS_NAME, self.server.ServiceBrowserNew(
                avahi.IF_UNSPEC,
                avahi.PROTO_UNSPEC,
                self.printer_type,
                "",
                dbus.UInt32(0)
            )),
            avahi.DBUS_INTERFACE_SERVICE_BROWSER
        )

        self.found_service = False
        def handle_service_found(*args):
            if args[2] == self.printer_name:
                self.found_service = True
                self.main_loop.quit()

        browser.connect_to_signal("ItemNew", handle_service_found)
        
        # Run main loop for 5 seconds or until service is found
        GLib.timeout_add(5000, lambda: self.main_loop.quit())
        self.main_loop.run()

        self.assertTrue(self.found_service)

        # Cleanup
        group.Reset()

    def test_02_browse_services(self):
        """Test browsing for printer services."""
        # Create a service browser
        browser = dbus.Interface(
            self.bus.get_object(avahi.DBUS_NAME, self.server.ServiceBrowserNew(
                avahi.IF_UNSPEC,
                avahi.PROTO_UNSPEC,
                "_ipp._tcp",
                "",
                dbus.UInt32(0)
            )),
            avahi.DBUS_INTERFACE_SERVICE_BROWSER
        )

        self.services = []
        def handle_service_found(*args):
            self.services.append(args[2])  # args[2] is the service name

        browser.connect_to_signal("ItemNew", handle_service_found)
        
        # Run main loop for 5 seconds
        GLib.timeout_add(5000, lambda: self.main_loop.quit())
        self.main_loop.run()

        # Verify we can discover services
        self.assertGreater(len(self.services), 0)

    def test_03_resolve_service(self):
        """Test resolving a printer service."""
        # First publish a service
        group = dbus.Interface(
            self.bus.get_object(avahi.DBUS_NAME, self.server.EntryGroupNew()),
            avahi.DBUS_INTERFACE_ENTRY_GROUP
        )

        group.AddService(
            avahi.IF_UNSPEC,
            avahi.PROTO_UNSPEC,
            dbus.UInt32(0),
            self.printer_name,
            self.printer_type,
            "",
            "",
            dbus.UInt16(self.printer_port),
            avahi.string_array_to_txt_array(["test=value"])
        )
        group.Commit()
        time.sleep(1)

        # Now try to resolve it
        self.resolved = False
        def handle_service_found(interface, protocol, name, stype, domain, flags):
            if name == self.printer_name:
                resolver = dbus.Interface(
                    self.bus.get_object(avahi.DBUS_NAME,
                        self.server.ServiceResolverNew(
                            interface,
                            protocol,
                            name,
                            stype,
                            domain,
                            avahi.PROTO_UNSPEC,
                            dbus.UInt32(0)
                        )
                    ),
                    avahi.DBUS_INTERFACE_SERVICE_RESOLVER
                )
                resolver.connect_to_signal("Found", self.handle_resolved)

        def handle_resolved(*args):
            self.resolved = True
            self.main_loop.quit()

        self.handle_resolved = handle_resolved

        browser = dbus.Interface(
            self.bus.get_object(avahi.DBUS_NAME, self.server.ServiceBrowserNew(
                avahi.IF_UNSPEC,
                avahi.PROTO_UNSPEC,
                self.printer_type,
                "",
                dbus.UInt32(0)
            )),
            avahi.DBUS_INTERFACE_SERVICE_BROWSER
        )

        browser.connect_to_signal("ItemNew", handle_service_found)
        
        # Run main loop for 5 seconds or until service is resolved
        GLib.timeout_add(5000, lambda: self.main_loop.quit())
        self.main_loop.run()

        self.assertTrue(self.resolved)

        # Cleanup
        group.Reset()

    def test_04_txt_record_update(self):
        """Test updating TXT records of a service."""
        # Create a service
        group = dbus.Interface(
            self.bus.get_object(avahi.DBUS_NAME, self.server.EntryGroupNew()),
            avahi.DBUS_INTERFACE_ENTRY_GROUP
        )

        group.AddService(
            avahi.IF_UNSPEC,
            avahi.PROTO_UNSPEC,
            dbus.UInt32(0),
            self.printer_name,
            self.printer_type,
            "",
            "",
            dbus.UInt16(self.printer_port),
            avahi.string_array_to_txt_array(["status=idle"])
        )
        group.Commit()
        time.sleep(1)

        # Update TXT record
        group.UpdateServiceTxt(
            avahi.IF_UNSPEC,
            avahi.PROTO_UNSPEC,
            dbus.UInt32(0),
            self.printer_name,
            self.printer_type,
            "",
            avahi.string_array_to_txt_array(["status=printing"])
        )
        time.sleep(1)

        # Verify update through resolving
        self.txt_record = None
        def handle_resolved(*args):
            self.txt_record = args[9]  # args[9] contains TXT records
            self.main_loop.quit()

        resolver = dbus.Interface(
            self.bus.get_object(avahi.DBUS_NAME,
                self.server.ServiceResolverNew(
                    avahi.IF_UNSPEC,
                    avahi.PROTO_UNSPEC,
                    self.printer_name,
                    self.printer_type,
                    "",
                    avahi.PROTO_UNSPEC,
                    dbus.UInt32(0)
                )
            ),
            avahi.DBUS_INTERFACE_SERVICE_RESOLVER
        )
        resolver.connect_to_signal("Found", handle_resolved)

        # Run main loop for 5 seconds or until TXT record is retrieved
        GLib.timeout_add(5000, lambda: self.main_loop.quit())
        self.main_loop.run()

        self.assertIsNotNone(self.txt_record)
        txt_dict = dict(t.split('=', 1) for t in avahi.txt_array_to_string_array(self.txt_record))
        self.assertEqual(txt_dict['status'], 'printing')

        # Cleanup
        group.Reset()

if __name__ == '__main__':
    unittest.main() 