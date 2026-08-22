# Delta: containerized-runtime

## ADDED Requirements

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
