#!/usr/bin/env bash
# ==============================================================================
# Home Assistant Add-on: RelayPrint Server
# Test script for verifying basic functionality
# ==============================================================================

# Exit on any error
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Test counter
TESTS_TOTAL=0
TESTS_FAILED=0

log_info() {
    echo -e "${YELLOW}INFO: $1${NC}"
}

log_success() {
    echo -e "${GREEN}✓ SUCCESS: $1${NC}"
}

log_error() {
    echo -e "${RED}✗ ERROR: $1${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

run_test() {
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
    local test_name=$1
    local test_cmd=$2
    local expected_exit_code=${3:-0}

    log_info "Running test: ${test_name}"
    
    if eval "${test_cmd}"; then
        if [ $? -eq ${expected_exit_code} ]; then
            log_success "${test_name}"
            return 0
        fi
    fi
    
    log_error "${test_name}"
    return 1
}

# Test 1: Verify Dockerfile exists and is valid
test_dockerfile() {
    run_test "Dockerfile validation" "docker run --rm -i hadolint/hadolint < Dockerfile"
}

# Test 2: Verify config.yaml/json syntax
test_config() {
    run_test "config.yaml syntax" "yamllint config.yaml"
    run_test "config.json syntax" "jq '.' config.json >/dev/null"
}

# Test 3: Check if all required files exist
test_files() {
    local required_files=(
        "Dockerfile"
        "config.yaml"
        "config.json"
        "run.sh"
        "rootfs/etc/services.d/cups/run"
        "rootfs/etc/services.d/avahi/run"
        "rootfs/etc/avahi/avahi-daemon.conf"
        "rootfs/etc/cont-init.d/01-persistent-storage.sh"
        "rootfs/etc/cont-init.d/02-cups-config.sh"
        "apparmor.txt"
    )
    
    for file in "${required_files[@]}"; do
        run_test "Required file check: ${file}" "test -f ${file}"
    done
}

# Test 4: Verify script permissions
test_permissions() {
    local executable_files=(
        "run.sh"
        "rootfs/etc/services.d/cups/run"
        "rootfs/etc/services.d/avahi/run"
        "rootfs/etc/cont-init.d/01-persistent-storage.sh"
        "rootfs/etc/cont-init.d/02-cups-config.sh"
    )
    
    for file in "${executable_files[@]}"; do
        run_test "Executable permission check: ${file}" "test -x ${file}"
    done
}

# Test 5: Build the add-on
test_build() {
    run_test "Add-on build" "docker build -t local/relayprint-test ."
}

# Test 6: Basic container startup
test_container() {
    run_test "Container startup" "docker run --rm -d --name relayprint-test local/relayprint-test"
    sleep 5
    run_test "CUPS process check" "docker exec relayprint-test pgrep cupsd"
    run_test "Avahi process check" "docker exec relayprint-test pgrep avahi-daemon"
    docker stop relayprint-test || true
}

# Run all tests
main() {
    log_info "Starting RelayPrint add-on tests"
    
    test_dockerfile
    test_config
    test_files
    test_permissions
    test_build
    test_container
    
    echo "----------------------------------------"
    echo "Test Summary:"
    echo "Total tests: ${TESTS_TOTAL}"
    echo "Failed tests: ${TESTS_FAILED}"
    
    if [ ${TESTS_FAILED} -eq 0 ]; then
        log_success "All tests passed!"
        exit 0
    else
        log_error "${TESTS_FAILED} test(s) failed!"
        exit 1
    fi
}

main "$@" 