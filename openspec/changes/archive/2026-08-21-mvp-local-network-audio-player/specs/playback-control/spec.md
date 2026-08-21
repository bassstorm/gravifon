## ADDED Requirements

### Requirement: Playback context is server-maintained
The system SHALL keep canonical playback context on the server, including active playlist, current track, playback mode, transport state, and playback position.

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

### Requirement: Playback modes include sequential and random
The system MUST support `sequential` and `random` playback modes that influence next-track selection.

#### Scenario: Sequential mode advances by order
- **WHEN** playback mode is sequential and next track is requested
- **THEN** the following track in playlist order is selected

#### Scenario: Random mode advances by random choice
- **WHEN** playback mode is random and next track is requested
- **THEN** the next track is chosen using random selection from the active playlist

#### Scenario: Random mode does not imply shuffle cycle
- **WHEN** playback mode is random in MVP
- **THEN** selection is pure random per step and SHALL NOT enforce shuffle-without-repeat behavior

### Requirement: Switching active playlist or mode eagerly resets current track selection
The system MUST select a current track immediately when the active playlist changes, rather than leaving current track selection to be resolved lazily on a later read.

#### Scenario: Active playlist changes
- **WHEN** a client switches the active playlist
- **THEN** the server immediately selects a current track from the new playlist consistent with the active playback mode, instead of deferring selection to the next state read

#### Scenario: Playback mode changes
- **WHEN** a client switches playback mode while a current track is selected
- **THEN** the current track selection is preserved if still valid for the active playlist, and only next-track selection uses the new mode

### Requirement: Playback context commands are entity-reference driven
The system SHALL support track selection, next-track selection, and mode updates through minimal, entity-reference-driven API commands, without the client performing playlist- or mode-specific selection logic itself.

#### Scenario: Prepare track context when playback starts
- **WHEN** a client requests playback preparation and no current track is selected
- **THEN** the server returns state with a selected current track so the client can start playback locally

#### Scenario: Client selects an explicit track
- **WHEN** a client selects a specific track by id from the active playlist
- **THEN** the server sets that track as current and resets position for the new track

#### Scenario: Next-track selection is server-driven
- **WHEN** a client requests the next track
- **THEN** the server selects the next track according to the active playback mode (sequential order or random choice) without the client performing that selection itself

### Requirement: Playback position is tracked with an optional client report and a passive fallback
The system MUST accept an optional, explicit client-reported playback position tied to a track id, and MUST fall back to a best-effort position derived from observed audio streaming activity for the current track when no report is present or a report is implausible. There is no dedicated seek command; reporting a new position is how both natural progress and seeking are expressed.

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

