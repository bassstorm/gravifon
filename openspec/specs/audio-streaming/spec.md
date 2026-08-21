# Audio Streaming Specification

## Purpose
Define how clients retrieve original catalog audio, including partial byte ranges, invalid-range handling, and playback-observation boundaries.

## Requirements

### Requirement: Audio is streamed without transcoding
The system SHALL stream original source audio bytes for supported catalog tracks without format conversion.

#### Scenario: Client requests stream for supported track
- **WHEN** a client requests audio for a cataloged track
- **THEN** the response payload contains bytes from the original file content

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
