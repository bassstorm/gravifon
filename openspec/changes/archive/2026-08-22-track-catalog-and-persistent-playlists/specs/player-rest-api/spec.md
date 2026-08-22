# Delta: player-rest-api

## MODIFIED Requirements

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

## ADDED Requirements

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
