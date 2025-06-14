#!/bin/bash

# Exit on error
set -e

# Install test dependencies if not already installed
if ! python3 -c "import pytest" 2>/dev/null; then
    echo "Installing test dependencies..."
    python3 -m pip install -r requirements.txt
fi

# Run tests with coverage
python3 -m pytest test_config.py -v --cov=. --cov-report=term-missing

# Return status
exit $? 