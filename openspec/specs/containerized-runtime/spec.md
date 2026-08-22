# Containerized Runtime Specification

## Purpose
Define the container image, mounted music-library, and runtime configuration contract for local-network and NAS deployment.

## Requirements

### Requirement: Application is delivered as Docker image
The system SHALL produce a Docker image artifact that runs the Spring Boot service and bundled static web assets.

#### Scenario: Build container image
- **WHEN** the build pipeline runs image packaging
- **THEN** a runnable image is produced for deployment

### Requirement: Runtime supports mounted music directory
The system MUST support mounting host music storage into container path `/music`.

#### Scenario: Compose-based local run with a host library
- **WHEN** a user starts the service through the committed `docker-compose.yaml` with `GRAVIFON_LIBRARY_PATH` set to a host music directory
- **THEN** the application scans that mounted directory at startup

#### Scenario: Compose file is available in repository
- **WHEN** a user clones the project and starts the service without host mount variables
- **THEN** a `docker-compose.yaml` is present and the app starts with Docker-managed fallback volumes

#### Scenario: Standalone container run on NAS
- **WHEN** a user runs the image as a standalone container with `/music` bind mount
- **THEN** the service starts and exposes API/UI using the mounted library

### Requirement: Runtime configuration is documented
The system SHALL document required environment variables, port mapping, and volume conventions for local and NAS deployment.

#### Scenario: Operator setup
- **WHEN** a new operator follows runtime documentation
- **THEN** they can launch Gravifon with predictable defaults and known required parameters

### Requirement: Runtime supports operator-selected library and configuration volumes
The system MUST support mounting host directories or named volumes into `/music` and `/config` through `GRAVIFON_LIBRARY_PATH` and `GRAVIFON_CONFIG_PATH`, and MUST provide Docker-managed fallback volumes when those variables are unset.

#### Scenario: Host library and configuration directories selected
- **WHEN** a user starts Compose with `GRAVIFON_LIBRARY_PATH` and `GRAVIFON_CONFIG_PATH` set to host directories
- **THEN** the library is mounted read-only at `/music`, SQLite state is stored at `/config`, and both locations are used by the application

#### Scenario: No host volume paths provided
- **WHEN** a user starts Compose without `GRAVIFON_LIBRARY_PATH` or `GRAVIFON_CONFIG_PATH`
- **THEN** Docker-managed fallback volumes are mounted at `/music` and `/config`, and the application starts normally

#### Scenario: Host configuration persists state
- **WHEN** a user sets `GRAVIFON_CONFIG_PATH` to a host directory
- **THEN** SQLite state under `/config` survives container removal and recreation

#### Scenario: Named volumes are supported
- **WHEN** an operator sets either volume path variable to a Docker named volume
- **THEN** Compose mounts that named volume at the corresponding application path

#### Scenario: Application locations are configurable
- **WHEN** an operator overrides `GRAVIFON_LIBRARY_DIR` or `GRAVIFON_CONFIG_DIR` outside Compose
- **THEN** the application scans the configured library location and stores its database in the configured config location

### Requirement: Runtime documentation covers config persistence
The system SHALL document the `/config` volume convention, its default behavior when unmounted, and its relationship to the `/music` mount.

#### Scenario: Operator setup with persistence
- **WHEN** a new operator follows runtime documentation
- **THEN** they can configure persistent application state with predictable defaults
