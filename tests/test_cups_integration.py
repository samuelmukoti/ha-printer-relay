#!/usr/bin/env python3
import unittest
import cups
import os
import time
import subprocess
from pathlib import Path
from contextlib import contextmanager

class TestCUPSIntegration(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        """Start CUPS server and wait for it to be ready."""
        # Create test directories
        os.makedirs("/tmp/cups-test/run", exist_ok=True)
        os.makedirs("/tmp/cups-test/log", exist_ok=True)
        os.makedirs("/tmp/cups-test/spool", exist_ok=True)

        # Start CUPS with test configuration
        cls.cups_process = subprocess.Popen([
            "cupsd",
            "-c", "tests/test_data/cupsd-test.conf",
            "-f"
        ])
        time.sleep(2)  # Wait for CUPS to start

        # Initialize CUPS connection
        cls.conn = cups.Connection()

    @classmethod
    def tearDownClass(cls):
        """Stop CUPS server and cleanup."""
        if cls.cups_process:
            cls.cups_process.terminate()
            cls.cups_process.wait()

    def setUp(self):
        """Setup test environment."""
        self.test_printer_name = "test_printer"
        self.pdf_file = "tests/test_data/test_print.pdf"

        # Create test PDF if it doesn't exist
        if not os.path.exists(self.pdf_file):
            Path(self.pdf_file).write_text("""
            %PDF-1.1
            1 0 obj
            <<
              /Type /Catalog
              /Pages 2 0 R
            >>
            endobj
            2 0 obj
            <<
              /Type /Pages
              /Kids [3 0 R]
              /Count 1
            >>
            endobj
            3 0 obj
            <<
              /Type /Page
              /Parent 2 0 R
              /MediaBox [0 0 612 792]
              /Resources <<>>
              /Contents 4 0 R
            >>
            endobj
            4 0 obj
            << /Length 44 >>
            stream
            BT
            /F1 24 Tf
            100 700 Td
            (Test Page) Tj
            ET
            endstream
            endobj
            xref
            0 5
            0000000000 65535 f
            0000000010 00000 n
            0000000063 00000 n
            0000000123 00000 n
            0000000234 00000 n
            trailer
            <<
              /Size 5
              /Root 1 0 R
            >>
            startxref
            327
            %%EOF
            """)

    def test_01_add_printer(self):
        """Test adding a new printer."""
        # Add a PDF printer for testing
        self.conn.addPrinter(
            self.test_printer_name,
            device="file:/dev/null",
            ppdname="raw",
            info="Test Printer",
            location="Test Location"
        )
        
        # Verify printer was added
        printers = self.conn.getPrinters()
        self.assertIn(self.test_printer_name, printers)
        
        # Enable the printer
        self.conn.enablePrinter(self.test_printer_name)
        self.conn.acceptJobs(self.test_printer_name)

    def test_02_printer_attributes(self):
        """Test getting printer attributes."""
        attrs = self.conn.getPrinterAttributes(self.test_printer_name)
        self.assertIsNotNone(attrs)
        self.assertEqual(attrs['printer-info'], "Test Printer")
        self.assertEqual(attrs['printer-location'], "Test Location")
        self.assertEqual(attrs['printer-state'], 3)  # Idle

    def test_03_print_job(self):
        """Test submitting and monitoring a print job."""
        # Submit print job
        job_id = self.conn.printFile(
            self.test_printer_name,
            self.pdf_file,
            "Test Job",
            {}
        )
        self.assertGreater(job_id, 0)

        # Get job attributes
        job = self.conn.getJobAttributes(job_id)
        self.assertIsNotNone(job)
        self.assertEqual(job['job-name'], "Test Job")

        # Wait for job to complete
        max_wait = 10
        while max_wait > 0:
            job = self.conn.getJobAttributes(job_id)
            if job['job-state'] in [9]:  # Completed
                break
            time.sleep(1)
            max_wait -= 1
        
        self.assertEqual(job['job-state'], 9)  # Completed

    def test_04_cancel_job(self):
        """Test canceling a print job."""
        # Submit a job
        job_id = self.conn.printFile(
            self.test_printer_name,
            self.pdf_file,
            "Job to Cancel",
            {}
        )
        
        # Cancel it immediately
        self.conn.cancelJob(job_id)
        
        # Verify job was canceled
        job = self.conn.getJobAttributes(job_id)
        self.assertEqual(job['job-state'], 7)  # Canceled

    def test_05_printer_state(self):
        """Test printer state changes."""
        # Disable printer
        self.conn.disablePrinter(self.test_printer_name)
        attrs = self.conn.getPrinterAttributes(self.test_printer_name)
        self.assertEqual(attrs['printer-state'], 5)  # Stopped
        
        # Enable printer
        self.conn.enablePrinter(self.test_printer_name)
        attrs = self.conn.getPrinterAttributes(self.test_printer_name)
        self.assertEqual(attrs['printer-state'], 3)  # Idle

    def test_06_reject_jobs(self):
        """Test rejecting jobs."""
        # Set printer to reject jobs
        self.conn.rejectJobs(self.test_printer_name)
        
        # Try to print - should raise exception
        with self.assertRaises(cups.IPPError):
            self.conn.printFile(
                self.test_printer_name,
                self.pdf_file,
                "Should Fail",
                {}
            )
        
        # Reset to accept jobs
        self.conn.acceptJobs(self.test_printer_name)

    def test_07_delete_printer(self):
        """Test deleting a printer."""
        self.conn.deletePrinter(self.test_printer_name)
        printers = self.conn.getPrinters()
        self.assertNotIn(self.test_printer_name, printers)

if __name__ == '__main__':
    unittest.main() 