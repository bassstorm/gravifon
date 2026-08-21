# Observability and Correlation Specification

## Purpose
Define structured diagnostics and correlation-id lifecycle behavior across API requests and playback operations.

## Requirements

### Requirement: Server logs include propagated CorrelationId
The system SHALL include request `CorrelationId` in log records emitted during handling of that request.

#### Scenario: Request flow logging
- **WHEN** a request with `CorrelationId` traverses filters, controllers, and services
- **THEN** all related logs include the same correlation value

#### Scenario: Missing CorrelationId fallback
- **WHEN** a request is received without `CorrelationId`
- **THEN** the server generates a correlation id, logs a warning about the missing header, and uses the generated id in all related logs

### Requirement: Structured logging is default runtime format
The system MUST emit structured logs by default in non-local runtime profiles.

#### Scenario: Container runtime
- **WHEN** the application runs with default/non-local profile
- **THEN** logs are emitted in structured format suitable for machine parsing

### Requirement: Local profile uses human-readable text logs
The system SHALL use regular text log formatting for local/dev profile.

#### Scenario: Local development run
- **WHEN** the application is started with local/dev profile
- **THEN** logs are rendered in human-readable text format

### Requirement: Logging severity semantics are consistent
The system MUST apply consistent log-level intent across server and client diagnostics.

#### Scenario: Log entry is emitted
- **WHEN** code emits a diagnostic log
- **THEN** it uses `trace` for high-detail troubleshooting, `debug` for diagnostics, `info` for key lifecycle events, `warn` for recoverable issue-like conditions, and `error` for actual failures

### Requirement: Correlation scope is tied to active operation lifecycle
The system MUST treat a correlation id as the trace scope for one active operation/resource lifecycle (for example track playback, playback mode change, or playlist switch).

#### Scenario: Playback continues within same track lifecycle
- **WHEN** events occur for the same selected track (play, pause, seek, metadata updates, buffering transitions)
- **THEN** related client and server logs reuse the same correlation id

#### Scenario: Lifecycle boundary is reached
- **WHEN** playback transitions to another track, active playlist is switched, playback mode is changed, or current track context is explicitly replaced
- **THEN** a new correlation id is created for the next lifecycle and subsequent logs use the new id

#### Scenario: Non-track operation starts a new lifecycle
- **WHEN** a client triggers playlist switch or playback mode change
- **THEN** the system starts a dedicated lifecycle with a new correlation id for that operation and related follow-up logs
