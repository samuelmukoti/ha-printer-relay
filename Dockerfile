ARG BUILD_FROM
FROM ${BUILD_FROM}

# Install required packages
RUN \
    apk add --no-cache \
        cups \
        cups-filters \
        cups-libs \
        cups-pdf \
        cups-client \
        avahi \
        avahi-compat-libdns_sd \
        avahi-tools \
        dbus \
        python3 \
        py3-pip \
        nginx \
        openssl \
        # Required for PDF processing
        ghostscript \
        imagemagick \
        # Required for printer detection
        udev \
        eudev \
        # Required for USB printers
        usbutils \
    && pip3 install --no-cache-dir supervisor \
    && mkdir -p /etc/cups \
    && mkdir -p /var/log/cups \
    && mkdir -p /var/spool/cups \
    && mkdir -p /var/run/dbus \
    && chmod 755 /var/log/cups \
    && chmod 710 /etc/cups \
    && chmod 700 /var/spool/cups

# Copy data
COPY rootfs /
COPY run.sh /
RUN chmod a+x /run.sh

# Create required directories with proper permissions
RUN \
    mkdir -p /data/cups/ssl \
    && mkdir -p /data/cups/ppd \
    && mkdir -p /data/cups/interface \
    && mkdir -p /data/printers \
    && mkdir -p /data/logs

# Labels
LABEL \
    io.hass.name="RelayPrint Server" \
    io.hass.description="Home Assistant add-on to relay print jobs via CUPS and secure tunnels" \
    io.hass.type="addon" \
    io.hass.version=${BUILD_VERSION} \
    io.hass.arch="armhf|aarch64|amd64|i386"

# Ports
EXPOSE 631

CMD [ "/run.sh" ]
