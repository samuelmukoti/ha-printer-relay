FROM ghcr.io/home-assistant/amd64-base-debian:latest
RUN apt-get update && apt-get install -y cups avahi-daemon cups-filters
COPY run.sh /run.sh
RUN chmod +x /run.sh
CMD ["/run.sh"]
