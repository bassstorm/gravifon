# Spec Delta

## MODIFIED Requirements

### Requirement: Invalid range requests are handled predictably
The system MUST reject unsatisfiable or unsupported byte ranges using standards-compliant error responses: `416 Range Not Satisfiable` carrying a `Content-Range: bytes */<length>` header. Multipart (multi-range) requests are out of contract and MUST be rejected the same way.

#### Scenario: Unsatisfiable range
- **WHEN** a client requests a byte range that starts beyond file length
- **THEN** the server responds with `416 Range Not Satisfiable` and a `Content-Range: bytes */<length>` header stating the resource length

#### Scenario: Malformed range
- **WHEN** a client requests a byte range that cannot be parsed or whose end precedes its start
- **THEN** the server responds with `416 Range Not Satisfiable` and a `Content-Range: bytes */<length>` header

#### Scenario: Multipart range request is rejected
- **WHEN** a client requests multiple byte ranges in a single request (e.g. `Range: bytes=0-99,500-599`)
- **THEN** the server responds with `416 Range Not Satisfiable` and a `Content-Range: bytes */<length>` header rather than a multipart payload

### Requirement: Expired stream URLs are refreshed before serving
The system MUST transparently refresh a stream-backed track's stream URL via the matching stream resolver when the URL is missing, expired, or has less than the configured minimum validity remaining at request time, before serving bytes. A track whose stream URL has no expiration timestamp is treated as non-expiring.

#### Scenario: Expired stream URL is refreshed
- **WHEN** a client requests audio for a stream-backed track whose stream URL is missing or past its expiration timestamp
- **THEN** the server resolves a fresh stream URL, persists it with its new expiration, and serves the audio

#### Scenario: Nearly expired stream URL is refreshed
- **WHEN** a client requests audio for a stream-backed track whose stream URL is still valid but expires within the configured minimum validity
- **THEN** the server resolves a fresh stream URL, persists it with its new expiration, and serves the audio from the fresh URL

#### Scenario: Refresh fails
- **WHEN** a stream URL refresh fails, times out, or another refresh for the same track is already in flight, and the track has no usable stream URL (missing or expired)
- **THEN** the server responds with a server error without serving partial content, and a failed refresh marks the track failing with a stream-unreachable error while leaving previously persisted state otherwise intact

#### Scenario: Refresh fails but the current stream URL is still valid
- **WHEN** a stream URL refresh fails or times out for a track whose persisted stream URL has not yet expired
- **THEN** the server serves the audio from the still-valid URL and does not mark the track failing

#### Scenario: Resolver returns an unusable expiration
- **WHEN** a resolver returns a stream URL whose expiration is not later than the minimum validity from now
- **THEN** the result is treated as a failed refresh attempt and is not persisted

#### Scenario: No resolver is registered for the stream source
- **WHEN** a stream URL refresh is needed but no resolver supports the track's source
- **THEN** the server marks the track failing and responds with a server error

### Requirement: Stream URL refresh is performed preemptively for upcoming playback
The system SHALL periodically refresh stream URLs ahead of expiration for the configured number of upcoming tracks (by default the current track and the nine that follow it in playback order), refreshing every stream-backed upcoming track whose URL is missing or expires within a configurable look-ahead window, on a configurable cron schedule that is independent of the look-ahead window. The schedule SHALL NOT trigger a run at startup and MAY be disabled by configuring the value `-`; a track that is not refreshed proactively is refreshed on demand when its stream is requested.

#### Scenario: Upcoming track is refreshed before expiry
- **WHEN** a stream-backed upcoming track has a stream URL that is still valid but expires within the configured look-ahead window
- **THEN** the scheduler refreshes and persists its stream URL before expiration

#### Scenario: Out-of-scope tracks are not refreshed
- **WHEN** a stream-backed track that is not among the configured number of upcoming tracks approaches expiration
- **THEN** the scheduler does not refresh it (refresh occurs lazily at stream-request time instead)

#### Scenario: Proactive refresh failure does not mark a still-valid track
- **WHEN** a scheduled refresh fails for a track whose persisted stream URL has not yet expired
- **THEN** the track is not marked failing and the scheduler retries on its next cycle

#### Scenario: Scheduler behavior is configurable
- **WHEN** an operator changes the look-ahead window, cron schedule, upcoming-track count, or minimum validity configuration
- **THEN** subsequent scheduler cycles honor the new configuration without code changes

#### Scenario: Proactive refresh can be disabled
- **WHEN** the refresh schedule is configured as `-`
- **THEN** no scheduled refresh runs and stream URLs are refreshed only when a stream is requested

#### Scenario: Missed proactive refresh falls back to request time
- **WHEN** a stream URL expires, or falls inside the minimum validity, before a scheduled run reaches its track
- **THEN** the stream request refreshes the URL before serving, with no startup-time constraint relating the schedule to the look-ahead window
