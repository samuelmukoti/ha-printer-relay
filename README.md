# RelayPrint Home Assistant Add-on

## Project Structure

- config.json: Home Assistant add-on configuration
- Dockerfile: Container build instructions (installs CUPS, Avahi)
- run.sh: Entry point script (starts CUPS, Avahi, placeholder for API)
- HA-PrinterServer.md: Product requirements and architecture
- LICENSE, repository.yaml: Metadata

## Entry Point

The add-on starts via `run.sh`, which launches dbus, Avahi, and CUPS services. Future development should add the print relay API and job management logic here.

## Development

- Extend `run.sh` or replace with a Python API server as needed.
- Use the requirements in HA-PrinterServer.md as a reference for features and architecture.

