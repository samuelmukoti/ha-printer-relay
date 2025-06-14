#!/usr/bin/with-contenv bashio
# ==============================================================================
# Home Assistant Add-on: RelayPrint Server
# Configures CUPS with secure defaults
# ==============================================================================

# Get configuration values
SSL_ENABLED=$(bashio::config 'ssl')
DEFAULT_USER=$(bashio::config 'default_user')
REMOTE_ACCESS=$(bashio::config 'remote_access')

# Configure CUPS
bashio::log.info "Configuring CUPS..."

# Basic settings
{
    echo "LogLevel $(bashio::config 'log_level')"
    echo "MaxLogSize 0"
    echo "PreserveJobHistory Yes"
    echo "PreserveJobFiles No"
    echo "MaxJobs $(bashio::config 'advanced.max_jobs')"
    echo "JobRetention $(bashio::config 'advanced.job_retention')"
} > /data/cups/cupsd.conf

# SSL Configuration
if bashio::var.true "${SSL_ENABLED}"; then
    {
        echo "SSLPort 631"
        echo "SSLListen *:631"
        echo "SSLCertificate /ssl/$(bashio::config 'certfile')"
        echo "SSLKey /ssl/$(bashio::config 'keyfile')"
    } >> /data/cups/cupsd.conf
else
    echo "Port 631" >> /data/cups/cupsd.conf
fi

# Access configuration
if bashio::var.true "${REMOTE_ACCESS}"; then
    echo "Listen *:631" >> /data/cups/cupsd.conf
else
    echo "Listen localhost:631" >> /data/cups/cupsd.conf
fi

# Add standard configuration
{
    echo "Listen /run/cups/cups.sock"
    echo "Browsing On"
    echo "BrowseLocalProtocols dnssd"
    echo "DefaultAuthType Basic"
    echo "WebInterface Yes"
} >> /data/cups/cupsd.conf

# Configure access control
{
    echo "<Location />"
    echo "  Order allow,deny"
    if bashio::var.true "${REMOTE_ACCESS}"; then
        echo "  Allow all"
    else
        echo "  Allow @LOCAL"
    fi
    echo "</Location>"
    
    echo "<Location /admin>"
    echo "  Order allow,deny"
    if bashio::var.true "${REMOTE_ACCESS}"; then
        echo "  Allow all"
    else
        echo "  Allow @LOCAL"
    fi
    echo "</Location>"
    
    echo "<Location /admin/conf>"
    echo "  AuthType Default"
    echo "  Require user @SYSTEM ${DEFAULT_USER}"
    echo "  Order allow,deny"
    if bashio::var.true "${REMOTE_ACCESS}"; then
        echo "  Allow all"
    else
        echo "  Allow @LOCAL"
    fi
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