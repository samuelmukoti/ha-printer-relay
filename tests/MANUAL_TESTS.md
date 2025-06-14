# Manual Testing Checklist

## Pre-Installation
- [ ] Verify Home Assistant instance is running
- [ ] Ensure USB printers are connected (if testing USB printing)
- [ ] Note any existing network printers for testing

## Installation Tests
- [ ] Add-on appears in Home Assistant add-on store
- [ ] Add-on installs without errors
- [ ] Add-on configuration page loads correctly
- [ ] All configuration options are visible and properly labeled
- [ ] Help text is clear and accurate

## Basic Functionality Tests
- [ ] Add-on starts successfully
- [ ] CUPS web interface is accessible through Home Assistant ingress
- [ ] Default admin login works
- [ ] SSL configuration works (if enabled)
- [ ] Avahi/mDNS discovery is working (printers appear in network)

## Printer Tests
### USB Printer
- [ ] USB printer is detected
- [ ] Can add USB printer through CUPS interface
- [ ] Can print test page to USB printer
- [ ] Printer status updates are visible

### Network Printer
- [ ] Network printer is discovered automatically
- [ ] Can add network printer manually
- [ ] Can print test page to network printer
- [ ] Printer status updates are visible

## Remote Access Tests
- [ ] Remote access works when enabled
- [ ] Remote access is blocked when disabled
- [ ] SSL certificate is properly applied for remote access

## iOS/Android Tests
- [ ] Printer appears in iOS print dialog
- [ ] Printer appears in Android print dialog
- [ ] Can print test document from iOS
- [ ] Can print test document from Android
- [ ] Print job status is reported correctly

## Error Handling Tests
- [ ] Appropriate error shown when printer is offline
- [ ] Appropriate error shown when paper is out
- [ ] Appropriate error shown when access is denied
- [ ] Error messages are clear and helpful

## Performance Tests
- [ ] Add-on starts within reasonable time
- [ ] Print jobs process without delay
- [ ] Multiple concurrent print jobs handled correctly
- [ ] Memory usage remains stable

## Security Tests
- [ ] Cannot access CUPS interface without authentication
- [ ] SSL certificate validation works
- [ ] Print jobs are isolated between users
- [ ] Sensitive information is not exposed in logs

## Recovery Tests
- [ ] Add-on recovers after Home Assistant restart
- [ ] Printer settings persist after add-on restart
- [ ] Print queue survives add-on restart
- [ ] Can recover from printer connection loss

## Notes
- Document any test failures with:
  - Steps to reproduce
  - Expected behavior
  - Actual behavior
  - Error messages/logs
  - System configuration

## Test Environment
- Home Assistant Version:
- Add-on Version:
- Host System:
- Printer Models:
- Network Configuration: 