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
    python3-dev

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

# Expose CUPS port
EXPOSE 631
