## ADDED Requirements

### Requirement: API exposes domain endpoints for tracks, playlists, and playback
The system SHALL provide REST endpoints for track listing/details, playlist listing/selection, and playback control/state.

#### Scenario: Client retrieves playback state
- **WHEN** a client calls the playback state endpoint
- **THEN** the response includes current track, playback mode, transport state, position, and active playlist

#### Scenario: Client reports transport transitions explicitly
- **WHEN** a client's playback transitions between playing, paused, and stopped
- **THEN** the client reports the transition via the transport command, and the server retains it as part of the canonical playback context

#### Scenario: Client lists tracks
- **WHEN** a client calls the tracks endpoint
- **THEN** the response contains catalog track summaries with stable identifiers

### Requirement: API surface separates data retrieval from state manipulation
The system MUST expose two distinct endpoint categories: data-retrieval endpoints that are read-only and free of side effects, and state-manipulation endpoints that accept entity references (playlist id, track id, playback mode, transport state) and return the resulting playback snapshot.

#### Scenario: Data-retrieval endpoint has no side effects
- **WHEN** a client calls a data-retrieval endpoint (tracks, playlists, playback state, or audio streaming)
- **THEN** the call does not authoritatively change server playback state as a documented consequence of that request

#### Scenario: State-manipulation endpoint is entity-reference driven
- **WHEN** a client calls a state-manipulation endpoint
- **THEN** the request identifies the target by domain identifier (e.g. playlist id, track id, mode, transport state) rather than by client-computed data, and the response reflects the updated playback snapshot

### Requirement: API is agnostic to client playback architecture
The system MUST express playback state and control purely through domain identifiers and generic operations that do not assume any particular client audio pipeline, buffering strategy, or streaming cadence.

#### Scenario: Alternate client architecture requires no API changes
- **WHEN** a client with a fully custom audio decode/buffering pipeline (not a browser HTML audio element) integrates against the API
- **THEN** it can fully drive playback using the same endpoints and payload shapes as the reference client, with no endpoint or contract changes required

#### Scenario: Position reporting does not assume a fetch cadence
- **WHEN** a client reports its current playback position
- **THEN** the report is a simple explicit value tied to a track id, independent of how or when that client fetched the underlying audio bytes

### Requirement: API contracts are future-client friendly
The system MUST use explicit resource identifiers and typed payloads that allow additional clients to consume the same API.

#### Scenario: Existing fields remain stable
- **WHEN** a new client integrates against MVP endpoints
- **THEN** resource IDs and documented response fields remain consistent for MVP-supported operations

### Requirement: API requests are always traceable by CorrelationId
The system MUST use a `CorrelationId` value for every client-server API request trace.

#### Scenario: Request includes CorrelationId
- **WHEN** a client sends `CorrelationId` with an API request
- **THEN** the request is processed and tracing uses the provided value

#### Scenario: Request omits CorrelationId
- **WHEN** a client sends an API request without `CorrelationId`
- **THEN** the server generates a new correlation id, logs a warning, and continues processing with the generated value

### Requirement: MVP API access model is single-user and stateless
The system MUST expose MVP APIs for a single-user local-network scenario without authentication, authorization, or server-side session management.

#### Scenario: API call is made in MVP mode
- **WHEN** a client calls an MVP endpoint
- **THEN** the request is processed without authn/authz checks and without creating or requiring a server session

#### Scenario: Multiple clients call API concurrently in MVP
- **WHEN** more than one client issues requests at the same time
- **THEN** the API behavior remains globally consistent for shared server playback context and does not establish per-user playback/session state

