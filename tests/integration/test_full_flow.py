#!/usr/bin/env python3
import os
import sys
import json
import time
import requests
import subprocess
from pathlib import Path

class PrinterRelayTester:
    def __init__(self):
        self.base_url = os.getenv('PRINTER_RELAY_URL', 'http://localhost:8080')
        self.auth_token = None
        self.test_printer = 'PDF'  # We'll use CUPS-PDF as our test printer
        
    def setup(self):
        """Setup test environment"""
        print("🔧 Setting up test environment...")
        
        # Check if Home Assistant add-on is running
        try:
            response = requests.get(f"{self.base_url}/api/health")
            if response.status_code != 200:
                print("❌ Home Assistant add-on is not running!")
                return False
            print("✅ Home Assistant add-on is running")
        except requests.exceptions.ConnectionError:
            print("❌ Cannot connect to Home Assistant add-on!")
            return False

        # Authenticate
        try:
            response = requests.post(
                f"{self.base_url}/api/auth",
                auth=('admin', 'admin')  # Default test credentials
            )
            if response.status_code == 200:
                self.auth_token = response.json()['token']
                print("✅ Authentication successful")
            else:
                print("❌ Authentication failed!")
                return False
        except Exception as e:
            print(f"❌ Authentication error: {e}")
            return False

        return True

    def test_printer_discovery(self):
        """Test printer discovery"""
        print("\n🔍 Testing printer discovery...")
        
        headers = {'Authorization': f'Bearer {self.auth_token}'}
        response = requests.get(f"{self.base_url}/api/printers", headers=headers)
        
        if response.status_code != 200:
            print("❌ Failed to list printers!")
            return False
            
        printers = response.json().get('printers', [])
        if not printers:
            print("❌ No printers found!")
            return False
            
        print(f"✅ Found {len(printers)} printer(s):")
        for printer in printers:
            print(f"  - {printer['name']} ({printer['status']})")
            
        return True

    def test_print_job(self):
        """Test submitting a print job"""
        print("\n📄 Testing print job submission...")
        
        # Create a test PDF
        test_pdf_path = "tests/test_data/test.pdf"
        
        headers = {
            'Authorization': f'Bearer {self.auth_token}'
        }
        
        # Submit print job
        with open(test_pdf_path, 'rb') as pdf:
            files = {
                'file': ('test.pdf', pdf, 'application/pdf')
            }
            data = {
                'printer_name': self.test_printer,
                'copies': '1',
                'duplex': 'false'
            }
            response = requests.post(
                f"{self.base_url}/api/print",
                headers=headers,
                files=files,
                data=data
            )
        
        if response.status_code != 200:
            print(f"❌ Failed to submit print job: {response.text}")
            return False
            
        job_id = response.json()['job_id']
        print(f"✅ Print job submitted (ID: {job_id})")
        
        # Monitor job status
        print("\n⏳ Monitoring job status...")
        max_attempts = 30
        attempt = 0
        
        while attempt < max_attempts:
            response = requests.get(
                f"{self.base_url}/api/print/{job_id}/status",
                headers=headers
            )
            
            if response.status_code != 200:
                print(f"❌ Failed to get job status: {response.text}")
                return False
                
            status = response.json()['status']
            print(f"  Status: {status}")
            
            if status in ['completed', 'failed']:
                break
                
            time.sleep(2)
            attempt += 1
        
        if status == 'completed':
            print("✅ Print job completed successfully")
            return True
        else:
            print("❌ Print job failed or timed out")
            return False

    def test_queue_status(self):
        """Test queue status"""
        print("\n📊 Testing queue status...")
        
        headers = {'Authorization': f'Bearer {self.auth_token}'}
        response = requests.get(f"{self.base_url}/api/queue/status", headers=headers)
        
        if response.status_code != 200:
            print("❌ Failed to get queue status!")
            return False
            
        status = response.json()
        print(f"✅ Queue status:")
        print(f"  Active jobs: {status['active_jobs']}")
        print(f"  Queued jobs: {status['queued_jobs']}")
        print(f"  Completed jobs: {status['completed_jobs']}")
        
        return True

    def run_all_tests(self):
        """Run all tests"""
        if not self.setup():
            return False
            
        tests = [
            self.test_printer_discovery,
            self.test_print_job,
            self.test_queue_status
        ]
        
        results = []
        for test in tests:
            try:
                result = test()
                results.append(result)
            except Exception as e:
                print(f"❌ Test failed with error: {e}")
                results.append(False)
        
        print("\n📝 Test Summary:")
        print(f"Total tests: {len(results)}")
        print(f"Passed: {results.count(True)}")
        print(f"Failed: {results.count(False)}")
        
        return all(results)

def main():
    tester = PrinterRelayTester()
    success = tester.run_all_tests()
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main() 