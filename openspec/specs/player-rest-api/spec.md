# Player REST API Specification

## Purpose
Define the client-facing REST contracts for catalog, playlists, playback, audio streaming, and request tracing.

## Requirements

### Requirement: API exposes domain endpoints for tracks, playlists, and playback
The system SHALL provide REST endpoints for track listing/details, track metadata update, track state reporting, playlist listing/selection/mutation, and playback control/state.

The playback state endpoint SHALL be read-only. Clients SHALL initialize a playback session with `POST /api/playback/init`.

#### Scenario: Client retrieves playback state
- **WHEN** a client calls the playback state endpoint
- **THEN** the response includes current track, playback mode, transport state, position, and active playlist

#### Scenario: Client reports transport transitions explicitly
- **WHEN** a client's playback transitions between playing, paused, and stopped
- **THEN** the client reports the transition via the transport command, and the server retains it as part of the canonical playback context

#### Scenario: Client lists tracks
- **WHEN** a client calls the tracks endpoint
- **THEN** the response contains track summaries for the full registry with stable identifiers

#### Scenario: Client retrieves track details
- **WHEN** a client calls the track details endpoint
- **THEN** the response includes the track's kind, full metadata map, duration, failure state, and — for stream tracks — source URL and expiration timestamp, but never the volatile stream URL

### Requirement: API supports playlist mutation
The system MUST expose endpoints to create playlists (from track ids, stream source URLs, or a full-registry materialization descriptor), rename playlists, reorder/add/remove entries, set per-playlist playback mode, and delete playlists.

#### Scenario: Client creates a playlist
- **WHEN** a client submits a new playlist with track ids and/or stream source URLs
- **THEN** the server persists the playlist and returns its representation with resolved track references

#### Scenario: Source URL resolves to multiple tracks
- **WHEN** a playlist creation includes a source URL that a resolver expands into multiple tracks
- **THEN** the created playlist references all resolved tracks

#### Scenario: Client mutates a playlist
- **WHEN** a client renames, reorders, adds or removes entries, or changes the mode of an existing playlist
- **THEN** the server applies and persists the change and returns the updated playlist

#### Scenario: Mutation references unknown track
- **WHEN** a playlist mutation references a track id that does not exist
- **THEN** the API responds with a client error and does not partially apply the mutation

### Requirement: API supports track metadata updates
The system MUST expose an endpoint for clients to update a track's metadata, applying the change to the single shared registry track.

#### Scenario: Client corrects metadata
- **WHEN** a client submits updated metadata values for a track
- **THEN** the registry track reflects the update and every playlist referencing the track serves the updated metadata

#### Scenario: Metadata update is source-transparent
- **WHEN** a client updates metadata for a file-backed track
- **THEN** the API response is identical in shape to a stream-track update, with any source write-back handled server-side and invisible to the client

### Requirement: API supports client track-state reports
The system MUST expose an endpoint for clients to report playback failures for a track, and MUST NOT automatically clear a track's failing state upon subsequent playback activity.

#### Scenario: Client reports a playback failure
- **WHEN** a client reports a failure for a track (e.g. stream request failed with a network or upstream error)
- **THEN** the server records the track as failing with the reported error details attributed to the client

#### Scenario: Client clears failure state
- **WHEN** a client explicitly requests clearing a track's failure state
- **THEN** the server resets the failing flag and last error for that track

#### Scenario: Server never auto-clears on playback
- **WHEN** audio bytes for a previously failing track are successfully streamed
- **THEN** the track's failing state remains unchanged unless explicitly cleared by a client or by a successful scan/refresh

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
- **WHEN** a new client integrates against the API
- **THEN** resource IDs and documented response fields remain consistent for supported operations

### Requirement: API requests are always traceable by CorrelationId
The system MUST use a `CorrelationId` value for every client-server API request trace.

#### Scenario: Request includes CorrelationId
- **WHEN** a client sends `CorrelationId` with an API request
- **THEN** the request is processed and tracing uses the provided value

#### Scenario: Request omits CorrelationId
- **WHEN** a client sends an API request without `CorrelationId`
- **THEN** the server generates a new correlation id, logs a warning, and continues processing with the generated value

### Requirement: API access model is single-user and stateless
The system MUST expose APIs for a single-user local-network scenario without authentication, authorization, or server-side session management.

#### Scenario: API call is made without authentication
- **WHEN** a client calls an API endpoint
- **THEN** the request is processed without authn/authz checks and without creating or requiring a server session

#### Scenario: Multiple clients call API concurrently
- **WHEN** more than one client issues requests at the same time
- **THEN** the API behavior remains globally consistent for shared server playback context and does not establish per-user playback/session state
