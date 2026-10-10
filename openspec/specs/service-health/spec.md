# Service Health Specification

## Purpose

Define the operational health surface of the service: health endpoint exposure, database and music-library indicators, and liveness/readiness probe semantics for containerized operation.

## Requirements

### Requirement: Service exposes an aggregated health endpoint
The system SHALL expose an HTTP health endpoint reporting aggregated service health, including at least the persistent track store (database) and the mounted music library path as named components.

#### Scenario: Healthy service
- **WHEN** the database is reachable and the music library path exists and is readable
- **THEN** the health endpoint reports overall UP with both components UP

#### Scenario: Music library path is unavailable
- **WHEN** the configured music library path does not exist or is not readable
- **THEN** the health endpoint reports the music library component as not UP with diagnostic detail

#### Scenario: Music library mount check never blocks the health endpoint
- **WHEN** the music library mount is unresponsive (e.g. a stalled network mount)
- **THEN** the health endpoint still responds promptly and reports the music library component as not UP

#### Scenario: Database is unavailable
- **WHEN** the persistent track store is unreachable
- **THEN** the health endpoint reports the database component as not UP

### Requirement: Readiness reflects serving capability
The system SHALL expose a readiness probe whose members are the database and the music library mount, so readiness reports UP exactly when the service can serve from its persisted state and library.

#### Scenario: Readiness is independent of the library scan
- **WHEN** the service has started with reachable database and music library mount, regardless of whether a library scan is pending, running, or failed
- **THEN** the readiness probe reports UP

#### Scenario: Music library mount is lost at runtime
- **WHEN** the music library mount becomes inaccessible after startup
- **THEN** the readiness probe reports the service as not ready until the mount is accessible again

#### Scenario: Database is unreachable
- **WHEN** the persistent track store is unreachable
- **THEN** the readiness probe reports the service as not ready

### Requirement: Liveness reflects process health only
The system SHALL expose a liveness probe reporting UP whenever the service process and application context are running, with no dependency on database, library mount, or scan state.

#### Scenario: Liveness during component failures
- **WHEN** the database, music library mount, or a library scan is failing while the application context is running
- **THEN** the liveness probe reports UP

### Requirement: Library scan outcomes are observable through health detail
The system SHALL report the most recent library scan outcome (completion time, success or failure with error detail) as a component of the health endpoint, without degrading the aggregated health status when a scan fails.

#### Scenario: Successful scan is recorded
- **WHEN** a library scan completes successfully
- **THEN** the health endpoint's scan component reports the successful outcome and completion time

#### Scenario: Failed scan is recorded without degrading health
- **WHEN** a library scan fails
- **THEN** the health endpoint's scan component reports the failure detail, the aggregated health status remains UP, and the failure is logged as an error
