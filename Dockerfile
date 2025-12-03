ARG BUILD_FROM
FROM ${BUILD_FROM}

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# Install required packages
RUN apk add --no-cache \
    cups \
    cups-filters \
    cups-dev \
    avahi \
    avahi-tools \
    dbus \
    python3 \
    py3-pip \
    gcc \
    musl-dev \
    python3-dev \
    curl \
    ca-certificates \
    nodejs \
    npm

# Install localtunnel globally for alternative tunnel provider
RUN npm install -g localtunnel

# Install cloudflared for remote tunnel access
# Detect architecture and download appropriate binary
RUN ARCH=$(uname -m) && \
    case "$ARCH" in \
        x86_64) CF_ARCH="amd64" ;; \
        aarch64) CF_ARCH="arm64" ;; \
        armv7l) CF_ARCH="arm" ;; \
        armhf) CF_ARCH="arm" ;; \
        *) echo "Unsupported architecture: $ARCH" && exit 1 ;; \
    esac && \
    curl -L "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${CF_ARCH}" \
        -o /usr/local/bin/cloudflared && \
    chmod +x /usr/local/bin/cloudflared

# Copy requirements and install Python packages
COPY requirements.txt /tmp/
RUN pip3 install --no-cache-dir --break-system-packages -r /tmp/requirements.txt

# Create necessary directories
RUN mkdir -p /data/cups /data/print_jobs /data/api \
    /usr/local/share/relayprint/templates \
    /usr/local/share/relayprint/static

# Save default CUPS config before rootfs copy
RUN mkdir -p /etc/cups.default && \
    cp /etc/cups/cupsd.conf /etc/cups.default/

# Copy rootfs
COPY rootfs/ /

# Remove legacy s6-overlay v2 services.d directory
RUN rm -rf /etc/services.d

# Set permissions for s6-overlay v3 run scripts and cont-init.d scripts
RUN find /etc/s6-overlay/s6-rc.d -type f -name run -exec chmod +x {} \; && \
    find /etc/cont-init.d -type f -exec chmod +x {} \;

# Ensure /init is executable (from base image)
RUN chmod +x /init

# Expose CUPS port
EXPOSE 631
