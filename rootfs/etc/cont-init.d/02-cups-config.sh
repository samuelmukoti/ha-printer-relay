#!/usr/bin/with-contenv bashio
# ==============================================================================
# Home Assistant Add-on: RelayPrint Server
# Configures CUPS with secure defaults
# ==============================================================================

# Get configuration values with defaults
CUPS_ADMIN_USER=$(bashio::config 'cups_admin_user' 2>/dev/null || echo "admin")
LOG_LEVEL=$(bashio::config 'log_level' 2>/dev/null || echo "info")
MAX_JOBS=$(bashio::config 'advanced.max_jobs' 2>/dev/null || echo "100")
JOB_RETENTION=$(bashio::config 'advanced.job_retention' 2>/dev/null || echo "24")

# Validate LogLevel (CUPS only accepts specific values)
case "${LOG_LEVEL}" in
    debug|debug2|info|warn|warning|error|notice) ;;
    *) LOG_LEVEL="info" ;;
esac

# Ensure numeric values
[[ "${MAX_JOBS}" =~ ^[0-9]+$ ]] || MAX_JOBS="100"
[[ "${JOB_RETENTION}" =~ ^[0-9]+$ ]] || JOB_RETENTION="24"

# Use defaults if empty
[[ -z "${CUPS_ADMIN_USER}" ]] && CUPS_ADMIN_USER="admin"

# Configure CUPS
bashio::log.info "Configuring CUPS..."

# Basic settings
{
    echo "LogLevel ${LOG_LEVEL}"
    echo "MaxLogSize 0"
    echo "PreserveJobHistory Yes"
    echo "PreserveJobFiles No"
    echo "MaxJobs ${MAX_JOBS}"
    echo "MaxJobTime 0"
    echo "Port 631"
    echo "Listen /run/cups/cups.sock"
    echo "Browsing On"
    echo "BrowseLocalProtocols dnssd"
    echo "DefaultAuthType Basic"
    echo "WebInterface Yes"
} > /data/cups/cupsd.conf

# Configure access control - allow local network access
# CUPS is accessed via Home Assistant ingress, so we allow local connections
{
    echo "<Location />"
    echo "  Order allow,deny"
    echo "  Allow @LOCAL"
    echo "</Location>"

    echo "<Location /admin>"
    echo "  Order allow,deny"
    echo "  Allow @LOCAL"
    echo "</Location>"

    echo "<Location /admin/conf>"
    echo "  AuthType Default"
    echo "  Require user @SYSTEM ${CUPS_ADMIN_USER}"
    echo "  Order allow,deny"
    echo "  Allow @LOCAL"
    echo "</Location>"
} >> /data/cups/cupsd.conf

# Add default policy
{
    echo "<Policy default>"
    echo "  JobPrivateAccess default"
    echo "  JobPrivateValues default"
    echo "  SubscriptionPrivateAccess default"
    echo "  SubscriptionPrivateValues default"

    echo "  <Limit Create-Job Print-Job Print-URI Validate-Job>"
    echo "    Order deny,allow"
    echo "  </Limit>"

    echo "  <Limit Send-Document Send-URI Hold-Job Release-Job Restart-Job Purge-Jobs Set-Job-Attributes Create-Job-Subscription Renew-Subscription Cancel-Subscription Get-Notifications Reprocess-Job Cancel-Current-Job Suspend-Current-Job Resume-Job Cancel-My-Jobs Close-Job CUPS-Move-Job CUPS-Get-Document>"
    echo "    Require user @OWNER @SYSTEM"
    echo "    Order deny,allow"
    echo "  </Limit>"

    echo "  <Limit Cancel-Job Cancel-Jobs>"
    echo "    Require user @OWNER @SYSTEM"
    echo "    Order deny,allow"
    echo "  </Limit>"

    echo "  <Limit CUPS-Add-Modify-Printer CUPS-Delete-Printer CUPS-Add-Modify-Class CUPS-Delete-Class CUPS-Set-Default CUPS-Get-Devices>"
    echo "    AuthType Default"
    echo "    Require user @SYSTEM"
    echo "    Order deny,allow"
    echo "  </Limit>"

    echo "  <Limit All>"
    echo "    Order deny,allow"
    echo "  </Limit>"
    echo "</Policy>"
} >> /data/cups/cupsd.conf

bashio::log.info "CUPS configuration completed" 