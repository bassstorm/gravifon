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

#### Scenario: Compose-based local run
- **WHEN** a user starts the service through the committed `docker-compose.yaml` using default host mount `~/Library` mapped to `/music`
- **THEN** the application scans that mounted directory at startup

#### Scenario: Compose file is available in repository
- **WHEN** a user clones the project and prepares local runtime
- **THEN** a `docker-compose.yaml` is present and defines the app service with `~/Library` mounted to `/music`

#### Scenario: Standalone container run on NAS
- **WHEN** a user runs the image as a standalone container with `/music` bind mount
- **THEN** the service starts and exposes API/UI using the mounted library

### Requirement: Runtime configuration is documented
The system SHALL document required environment variables, port mapping, and volume conventions for local and NAS deployment.

#### Scenario: Operator setup
- **WHEN** a new operator follows runtime documentation
- **THEN** they can launch Gravifon with predictable defaults and known required parameters
