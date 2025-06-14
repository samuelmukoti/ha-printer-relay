#!/bin/bash

# Exit on error
set -e

# Move to the project root directory
cd "$(dirname "$0")/.."

# Build the test Docker image
echo "Building test Docker image..."
docker build -t relayprint-tests -f tests/Dockerfile.test .

# Run tests in Docker container
echo "Running tests in Docker container..."
docker run --rm relayprint-tests

# Return status
exit $? 