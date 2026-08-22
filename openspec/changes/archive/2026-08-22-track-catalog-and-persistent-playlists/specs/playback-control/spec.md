# Delta: playback-control

## MODIFIED Requirements

### Requirement: Playback context is server-maintained
The system SHALL keep canonical playback context on the server, including active playlist, current track, transport state, and playback position with its origin, and SHALL persist this context durably so it survives application restarts. Playback mode is maintained per playlist (see playlist-management) rather than as a global field.

#### Scenario: Multiple clients read player state
- **WHEN** two clients request current playback status
- **THEN** both receive the same server state snapshot

#### Scenario: Transport state is server-maintained
- **WHEN** a client reports a transport transition (playing, paused, or stopped)
- **THEN** the server retains that transport state as part of the canonical playback context until the next transport report

#### Scenario: Stopped transport resets position
- **WHEN** a client reports transport state `stopped`
- **THEN** the server resets the bookmarked position for the current track to zero

#### Scenario: Playback context survives client reconnects
- **WHEN** a client reconnects (e.g. a new browser tab is opened) after another client last updated playback context
- **THEN** the reconnecting client receives the same active playlist, current track, playback mode, transport state, and position previously recorded, with no server-side session required to do so

#### Scenario: Playback context survives server restart
- **WHEN** the application restarts
- **THEN** the persisted active playlist, current track, transport state, and position are restored

### Requirement: Playback modes include sequential and random
The system MUST support `sequential` and `random` playback modes that influence next-track selection, with the active mode taken from the active playlist's stored mode.

#### Scenario: Sequential mode advances by order
- **WHEN** playback mode is sequential and next track is requested
- **THEN** the following track in playlist order is selected

#### Scenario: Random mode advances by random choice
- **WHEN** playback mode is random and next track is requested
- **THEN** the next track is chosen using random selection from the active playlist

#### Scenario: Random mode does not imply shuffle cycle
- **WHEN** playback mode is random
- **THEN** selection is pure random per step and SHALL NOT enforce shuffle-without-repeat behavior

### Requirement: Playback position is tracked with an optional client report and a passive fallback
The system MUST accept an optional, explicit client-reported playback position tied to a track id, and MUST fall back to a best-effort position derived from observed audio streaming activity for the current track when no report is present or a report is implausible. The effective position and its origin (client-reported or observed) SHALL be persisted as part of the playback context. There is no dedicated seek command; reporting a new position is how both natural progress and seeking are expressed.

#### Scenario: Client reports position explicitly
- **WHEN** a client reports its current playback position for the current track
- **THEN** the server records that position as authoritative for as long as it remains plausible

#### Scenario: Reported position exceeds observed streamed data
- **WHEN** a client-reported position implies playback beyond what has actually been streamed for the current track
- **THEN** the server treats the report as implausible and falls back to its best-effort observed position instead

#### Scenario: No explicit position report is available
- **WHEN** no client has explicitly reported a position for the current track
- **THEN** the server falls back to a best-effort position derived from observed audio streaming activity for that track

#### Scenario: Stale reports for a replaced track are ignored
- **WHEN** a position report arrives for a track id that is no longer the current track
- **THEN** the server ignores that report

#### Scenario: Position is restored after restart
- **WHEN** the application restarts while a position was recorded for the current track
- **THEN** the restored playback context includes the last effective position

## ADDED Requirements

### Requirement: Playing transport state is normalized when no player is attached
The system SHALL transition a persisted or presumed `playing` transport state to `paused`, retaining the recorded position, whenever playback resumes visibility without an actively playing client.

#### Scenario: Server restarts while playing
- **WHEN** the application restarts and the persisted transport state was `playing`
- **THEN** the restored playback context reports transport state `paused` at the recorded position

#### Scenario: Client init while server believes playback is active
- **WHEN** a client issues an init/connect request and the current transport state is `playing`
- **THEN** the server transitions the transport state to `paused` at the recorded position and returns the updated snapshot

### Requirement: Playback context is session-keyed
The system SHALL store playback context keyed by a session identifier, using a single default session until explicit session support exists.

#### Scenario: All clients share the default session
- **WHEN** any client issues playback commands without a session identifier
- **THEN** the commands read and mutate the shared default session's playback context
