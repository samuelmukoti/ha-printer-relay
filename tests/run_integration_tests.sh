#!/usr/bin/env bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}🚀 Starting integration tests...${NC}"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

# Create test network if it doesn't exist
if ! docker network inspect printer-test-net > /dev/null 2>&1; then
    echo -e "${YELLOW}Creating test network...${NC}"
    docker network create printer-test-net
fi

# Build the test container
echo -e "${YELLOW}Building test environment...${NC}"
docker build -t printer-relay-test -f tests/Dockerfile.test .

# Start CUPS-PDF container for testing
echo -e "${YELLOW}Starting CUPS-PDF container...${NC}"
docker run -d \
    --name cups-pdf-test \
    --network printer-test-net \
    --volume $(pwd)/tests/test_data/print_jobs:/var/spool/cups-pdf/ANONYMOUS \
    athernik/cups-pdf:latest

# Start the printer relay container
echo -e "${YELLOW}Starting printer relay container...${NC}"
docker run -d \
    --name printer-relay-test \
    --network printer-test-net \
    -e API_SECRET=test-secret \
    -e TOKEN_EXPIRY=3600 \
    -p 8080:8080 \
    -v $(pwd)/tests/test_data:/data \
    printer-relay-test

# Wait for services to be ready
echo -e "${YELLOW}Waiting for services to be ready...${NC}"
sleep 10

# Run the integration tests
echo -e "${YELLOW}Running integration tests...${NC}"
python3 tests/integration/test_full_flow.py

# Cleanup
echo -e "${YELLOW}Cleaning up test environment...${NC}"
docker stop printer-relay-test cups-pdf-test
docker rm printer-relay-test cups-pdf-test
docker network rm printer-test-net

echo -e "${GREEN}✅ Integration tests completed${NC}" 