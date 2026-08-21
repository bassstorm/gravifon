# Playlist Management Specification

## Purpose
Define in-memory playlist lifecycle, default all-tracks behavior, and the supported playlist operations.

## Requirements

### Requirement: Server maintains in-memory playlists
The system SHALL manage playlists in memory and reset playlist state on application restart.

#### Scenario: Restart clears runtime playlist mutations
- **WHEN** the application restarts
- **THEN** playlists are reconstructed from startup sources and previous runtime state is not persisted

### Requirement: Default all-tracks playlist always exists
The system MUST create a default playlist containing all indexed tracks after catalog scan, even when no playlist files are present.

#### Scenario: No playlist files exist
- **WHEN** startup scan finds tracks but no supported playlist files
- **THEN** the default all-tracks playlist exists and is selectable

#### Scenario: Catalog is empty
- **WHEN** startup scan finds no supported tracks
- **THEN** the default playlist still exists with zero entries

### Requirement: Playlist view is read-only for client UX
The system MUST support playlist listing and active playlist selection, while playlist mutation operations may be unavailable.

#### Scenario: Client selects a playlist
- **WHEN** a client requests to set an available playlist as active
- **THEN** playback context updates to that playlist

#### Scenario: Client requests unsupported mutation
- **WHEN** a client calls create, update, or delete playlist operations that are not implemented
- **THEN** the API responds with `501 Not Implemented`
