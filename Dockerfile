ARG BUILD_FROM=ghcr.io/home-assistant/amd64-base:3.20
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
    openssl \
    bash

# Install build dependencies, Python packages, then remove build deps
COPY requirements.txt /tmp/
RUN apk add --no-cache --virtual .build-deps \
    gcc \
    musl-dev \
    python3-dev \
    && pip3 install --no-cache-dir --break-system-packages -r /tmp/requirements.txt \
    && apk del .build-deps

# Create necessary directories
RUN mkdir -p /data/cups /data/print_jobs /data/api \
    /usr/local/share/relayprint/templates \
    /usr/local/share/relayprint/static

# Copy rootfs
COPY rootfs /

# Remove old services.d structure (using s6-overlay v3 s6-rc.d instead)
RUN rm -rf /etc/services.d

# Make s6-overlay v3 service scripts executable
RUN chmod +x /etc/s6-overlay/s6-rc.d/*/run

# Make init scripts executable
RUN chmod +x /etc/cont-init.d/*.sh
