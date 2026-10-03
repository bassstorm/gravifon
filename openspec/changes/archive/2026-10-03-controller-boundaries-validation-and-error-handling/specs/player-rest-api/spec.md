## MODIFIED Requirements

### Requirement: API surface separates data retrieval from state manipulation
The system MUST expose two distinct endpoint categories: data-retrieval endpoints that are read-only and free of side effects, and state-manipulation endpoints that accept entity references (playlist id, track id, playback mode, transport state) and return the resulting playback snapshot.

#### Scenario: Data-retrieval endpoint has no side effects
- **WHEN** a client calls a data-retrieval endpoint (tracks, playlists, playback state, or audio streaming)
- **THEN** the call does not authoritatively change server playback state as a documented consequence of that request

#### Scenario: State-manipulation endpoint is entity-reference driven
- **WHEN** a client calls a state-manipulation endpoint
- **THEN** the request identifies the target by domain identifier (e.g. playlist id, track id, mode, transport state) rather than by client-computed data, and the response reflects the updated playback snapshot

#### Scenario: Mode and transport state commands accept valid enum values
- **WHEN** a client calls `/api/playback/mode/{mode}` or `/api/playback/transport/{transportState}` with an exact uppercase enum value (e.g., `RANDOM`, `PLAYING`)
- **THEN** the server updates playback state and returns the snapshot

#### Scenario: Mode and transport state commands reject invalid enum values
- **WHEN** a client calls `/api/playback/mode/{mode}` or `/api/playback/transport/{transportState}` with an unrecognized enum name
- **THEN** the server responds with a 400 Bad Request error response and does not alter playback state

### Requirement: API supports track metadata updates
The system MUST expose an endpoint for clients to update a track's metadata, applying the change to the single shared registry track.

#### Scenario: Client corrects metadata
- **WHEN** a client submits updated metadata values for a track
- **THEN** the registry track reflects the update and every playlist referencing the track serves the updated metadata

#### Scenario: Metadata update is source-transparent
- **WHEN** a client updates metadata for a file-backed track
- **THEN** the API response is identical in shape to a stream-track update, with any source write-back handled server-side and invisible to the client

#### Scenario: Metadata update for non-existent track
- **WHEN** a client submits a metadata update for a track id that does not exist in the registry
- **THEN** the server responds with a 404 Not Found error response

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

#### Scenario: State report for non-existent track
- **WHEN** a client submits a state report for a track id that does not exist in the registry
- **THEN** the server responds with a 404 Not Found error response

## ADDED Requirements

### Requirement: API validates request payloads declaratively
The system MUST validate all client-supplied mutation and report request payloads using declarative constraints before executing application logic, rejecting invalid payloads with standardized 400 Bad Request responses.

#### Scenario: Missing or blank required field
- **WHEN** a client submits a playlist mutation request with a missing or blank playlist name
- **THEN** the server rejects the request with HTTP 400 Bad Request and details indicating the invalid field

#### Scenario: Malformed request payload
- **WHEN** a client sends a malformed JSON request body or payload containing unparseable values
- **THEN** the server rejects the request with HTTP 400 Bad Request

#### Scenario: Invalid position report
- **WHEN** a client reports a playback position with a negative position seconds value
- **THEN** the server rejects the request with HTTP 400 Bad Request
