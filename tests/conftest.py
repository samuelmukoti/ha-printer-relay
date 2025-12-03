"""
Pytest configuration for RelayPrint tests.

Sets up environment and mocks before test modules import print_api.
"""
import os
import sys
import tempfile

# Create temp directory for uploads
TEST_UPLOAD_FOLDER = tempfile.mkdtemp()
os.environ['UPLOAD_FOLDER'] = TEST_UPLOAD_FOLDER

# Add the source code to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'rootfs', 'usr', 'local', 'bin'))

# Patch os.makedirs to not fail on /data
_original_makedirs = os.makedirs

def _patched_makedirs(name, mode=0o777, exist_ok=False):
    # Convert PosixPath to string if needed
    name_str = str(name)
    if name_str.startswith('/data'):
        name = os.path.join(TEST_UPLOAD_FOLDER, name_str.lstrip('/data/'))
    return _original_makedirs(name, mode, exist_ok)

os.makedirs = _patched_makedirs
