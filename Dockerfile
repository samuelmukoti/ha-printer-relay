ARG BUILD_FROM=ghcr.io/home-assistant/amd64-base:3.22
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

# Remove old services.d if exists and set permissions
RUN rm -rf /etc/services.d && \
    chmod +x /etc/s6-overlay/s6-rc.d/relayprint/run
