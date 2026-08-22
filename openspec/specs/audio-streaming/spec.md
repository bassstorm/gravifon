# Audio Streaming Specification

## Purpose
Define how clients retrieve original catalog audio, including partial byte ranges, invalid-range handling, and playback-observation boundaries.

## Requirements

### Requirement: Audio is streamed without transcoding
The system SHALL stream original source audio bytes for supported catalog tracks without format conversion, regardless of whether the track is file-backed or stream-backed. Clients MUST NOT be required to distinguish track kinds when requesting audio.

#### Scenario: Client requests stream for supported track
- **WHEN** a client requests audio for a file-backed catalog track
- **THEN** the response payload contains bytes from the original file content

#### Scenario: Client requests stream for stream-backed track
- **WHEN** a client requests audio for a stream-backed catalog track
- **THEN** the server proxies bytes from the track's current stream URL and the response payload contains the original source audio bytes

#### Scenario: Stream URL is never exposed
- **WHEN** a client requests audio for a stream-backed track
- **THEN** the response does not redirect to or reveal the underlying stream URL

### Requirement: Expired stream URLs are refreshed before serving
The system MUST transparently refresh a stream-backed track's stream URL via the matching stream resolver when the URL is missing or expired at request time, before serving bytes.

#### Scenario: Expired stream URL is refreshed
- **WHEN** a client requests audio for a stream-backed track whose stream URL is missing or past its expiration timestamp
- **THEN** the server resolves a fresh stream URL, persists it with its new expiration, and serves the audio

#### Scenario: Refresh fails
- **WHEN** a stream URL refresh fails, times out, or another refresh for the same track is already in flight
- **THEN** the server responds with a server error without serving partial content, and a failed refresh marks the track failing with a stream-unreachable error while leaving previously persisted state otherwise intact

#### Scenario: No resolver is registered for the stream source
- **WHEN** a stream URL refresh is needed but no resolver supports the track's source
- **THEN** the server marks the track failing and responds with a server error

### Requirement: Stream URL refresh is performed preemptively for upcoming playback
The system SHALL periodically refresh stream URLs ahead of expiration for tracks in the active playlist plus the next-track candidate, within a configurable look-ahead window and cadence.

#### Scenario: Upcoming track is refreshed before expiry
- **WHEN** a stream-backed track in the refresh scope has a stream URL expiring within the configured look-ahead window
- **THEN** the scheduler refreshes and persists its stream URL before expiration

#### Scenario: Out-of-scope tracks are not refreshed
- **WHEN** a stream-backed track outside the active playlist approaches expiration
- **THEN** the scheduler does not refresh it (refresh occurs lazily at stream-request time instead)

#### Scenario: Scheduler behavior is configurable
- **WHEN** an operator changes the look-ahead window, cadence, or scope configuration
- **THEN** subsequent scheduler cycles honor the new configuration without code changes

### Requirement: Proxied streams forward range requests to the upstream source
When serving stream-backed tracks, the system SHALL forward client HTTP range headers to the upstream source and pass through partial-content responses, and MUST remain correct when the upstream does not support ranges.

#### Scenario: Range request against a stream-backed track
- **WHEN** a client requests a byte range for a stream-backed track whose upstream supports ranges
- **THEN** the server responds with the corresponding partial content

#### Scenario: Upstream does not support ranges
- **WHEN** a client requests a byte range for a stream-backed track whose upstream ignores range headers
- **THEN** the server responds correctly from the upstream's full-content response without a protocol error

### Requirement: HTTP range requests are supported
The system MUST support valid HTTP range requests so clients can seek and buffer with partial content responses.

#### Scenario: Partial byte range request
- **WHEN** a client requests `Range: bytes=1000-1999` for a track
- **THEN** the server responds with `206 Partial Content` and the requested byte segment

#### Scenario: Open-ended range request
- **WHEN** a client requests `Range: bytes=5000-`
- **THEN** the server responds with bytes from the requested offset to the end of file

### Requirement: Invalid range requests are handled predictably
The system MUST reject unsatisfiable byte ranges using standards-compliant error responses.

#### Scenario: Unsatisfiable range
- **WHEN** a client requests a byte range that starts beyond file length
- **THEN** the server responds with `416 Range Not Satisfiable`

### Requirement: Streaming remains free of authoritative playback-state side effects
The system MUST treat audio byte streaming as a data-retrieval operation that never authoritatively mutates playback state, while still allowing best-effort position observation for the current track as described by the playback-control capability's position fallback.

#### Scenario: Streaming a non-current track has no effect on playback state
- **WHEN** a client requests audio bytes for a track that is not the current track (e.g. prefetching or caching)
- **THEN** the request does not change the server's active playlist, current track, transport state, or position

#### Scenario: Streaming activity never overrides an explicit, plausible position report
- **WHEN** a client has explicitly reported a plausible playback position for the current track
- **THEN** observed streaming activity for that track does not override the explicitly reported position
