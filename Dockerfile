FROM ghcr.io/home-assistant/amd64-base:3.18

# Install required packages
RUN apk add --no-cache \
    cups \
    cups-filters \
    cups-pdf \
    avahi \
    dbus \
    python3 \
    py3-pip \
    nginx \
    bash

# Install Python dependencies
COPY requirements.txt /tmp/
RUN pip3 install --no-cache-dir -r /tmp/requirements.txt

# Create necessary directories
RUN mkdir -p /data/cups /data/print_jobs

# Copy configuration files
COPY rootfs /
COPY run.sh /
RUN chmod a+x /run.sh

# Copy API and application files
COPY rootfs/usr/local/bin/print_api.py /usr/local/bin/
COPY rootfs/usr/local/bin/job_queue_manager.py /usr/local/bin/
COPY rootfs/usr/local/bin/printer_discovery.py /usr/local/bin/

# Set up CUPS
RUN mkdir -p /etc/cups
COPY rootfs/etc/cups/cupsd.conf /etc/cups/
RUN chmod 640 /etc/cups/cupsd.conf

# Set up Avahi
COPY rootfs/etc/avahi/avahi-daemon.conf /etc/avahi/
RUN chmod 644 /etc/avahi/avahi-daemon.conf

# Set up Nginx
COPY rootfs/etc/nginx/nginx.conf /etc/nginx/
RUN chmod 644 /etc/nginx/nginx.conf

# Create data directory
VOLUME ["/data"]

# Expose ports
EXPOSE 631 8080

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV API_SECRET=change-me-in-production
ENV TOKEN_EXPIRY=86400

# Start services
CMD ["/run.sh"]

# Labels
LABEL \
    io.hass.name="Open-ReplayPrinter" \
    io.hass.description="CUPS print server with remote access via Home Assistant" \
    io.hass.type="addon" \
    io.hass.version="0.1.0" \
    maintainer="Your Name <your.email@example.com>"
