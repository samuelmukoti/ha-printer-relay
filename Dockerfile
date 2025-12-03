ARG BUILD_FROM
FROM ${BUILD_FROM}

# Install required packages
RUN apk add --no-cache \
    cups \
    cups-filters \
    cups-dev \
    avahi \
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
    && pip3 install --no-cache-dir -r /tmp/requirements.txt \
    && apk del .build-deps

# Create necessary directories
RUN mkdir -p /data/cups /data/print_jobs /data/api

# Copy configuration files
COPY rootfs /
COPY run.sh /
RUN chmod a+x /run.sh

# Copy API and application files
COPY rootfs/usr/local/bin/print_api.py /usr/local/bin/
COPY rootfs/usr/local/bin/job_queue_manager.py /usr/local/bin/
COPY rootfs/usr/local/bin/printer_discovery.py /usr/local/bin/

# Set up CUPS default config (will be overwritten by init scripts)
RUN mkdir -p /etc/cups.default
COPY rootfs/etc/cups/cupsd.conf /etc/cups.default/
RUN chmod 640 /etc/cups.default/cupsd.conf

# Set up Avahi
COPY rootfs/etc/avahi/avahi-daemon.conf /etc/avahi/
RUN chmod 644 /etc/avahi/avahi-daemon.conf

# Make init scripts and services executable
RUN chmod +x /etc/cont-init.d/*.sh && \
    chmod +x /etc/services.d/*/run

# Create data directory
VOLUME ["/data"]

# Expose ports: CUPS (631), Avahi (5353), API (7779)
EXPOSE 631 5353 7779

# Set environment variables (fallbacks for development)
ENV PYTHONUNBUFFERED=1

# Labels
LABEL \
    io.hass.name="RelayPrint" \
    io.hass.description="CUPS print server with remote access via Home Assistant" \
    io.hass.type="addon" \
    io.hass.version="0.1.0"
