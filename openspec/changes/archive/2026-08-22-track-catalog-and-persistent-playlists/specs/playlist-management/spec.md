# Delta: playlist-management

## REMOVED Requirements

### Requirement: Server maintains in-memory playlists
**Reason**: Playlists become persistent, database-backed entities that survive restarts; in-memory rebuild semantics are replaced by durable storage with scan reconciliation.
**Migration**: No persisted data exists under the old model (playlists were ephemeral). On first boot with this change, the registry is populated by scan; any client assumptions about playlist loss on restart must be revisited.

### Requirement: Default all-tracks playlist always exists
**Reason**: The default-playlist concept is replaced by two ordinary behaviors: catalog browsing via the track listing endpoint, and on-demand materialization of the catalog into a normal persisted playlist at client request. No special or forced playlists exist.
**Migration**: Clients that relied on the implicit all-tracks playlist now either browse tracks directly or explicitly create a playlist from the catalog.

### Requirement: Playlist view is read-only for client UX
**Reason**: Playlist mutation (create, rename, reorder, add/remove entries, delete, per-playlist playback mode) is now a core supported workflow, replacing the 501-read-only contract.
**Migration**: Clients previously receiving `501 Not Implemented` from mutation endpoints now receive functional responses; clients should handle success payloads and validation errors instead.

## ADDED Requirements

### Requirement: Playlists are persisted and survive restarts
The system SHALL durably store playlists — including name, ordered track references, and per-playlist playback mode — so they survive application restarts. The system SHALL NOT create any playlist implicitly; all playlists are user-created.

#### Scenario: Restart preserves playlists
- **WHEN** the application restarts
- **THEN** all user playlists exist with their previous names, entry order, track references, and playback modes

#### Scenario: Fresh install has no playlists
- **WHEN** the application starts with an empty playlist store
- **THEN** no playlists exist until a client creates one

### Requirement: Playlist entries reference shared registry tracks
The system MUST model playlist entries as references to registry tracks by identity, such that track metadata and state updates are visible identically in every playlist referencing that track.

#### Scenario: Metadata edit propagates to all playlists
- **WHEN** a track's metadata is updated and multiple playlists reference that track
- **THEN** every playlist view reflects the updated metadata without per-playlist edits

#### Scenario: Failure state is visible across playlists
- **WHEN** a track is marked failing
- **THEN** every playlist containing that track shows its failing state

#### Scenario: Track appears multiple times
- **WHEN** the same track is added to a playlist more than once or to several playlists
- **THEN** each entry references the single shared registry track

### Requirement: Clients can create and mutate playlists
The system MUST support creating playlists from track ids, from stream source URLs, or by materializing the full track registry, plus renaming, reordering entries, adding and removing entries, setting playback mode, and deleting playlists.

#### Scenario: Create playlist from track ids
- **WHEN** a client creates a playlist referencing existing registry track ids
- **THEN** the playlist is persisted with entries in the supplied order

#### Scenario: Create playlist including stream links
- **WHEN** a client creates a playlist including one or more stream source URLs
- **THEN** each URL is resolved into one or more registry stream tracks, and the playlist references all resolved tracks

#### Scenario: Materialize catalog as playlist
- **WHEN** a client requests creation of a playlist from the full track registry
- **THEN** a persisted playlist is created containing all current registry tracks, and it is thereafter an ordinary playlist not auto-updated by registry changes

#### Scenario: Reorder entries
- **WHEN** a client reorders a playlist's entries
- **THEN** subsequent reads return the new order and the order survives restart

#### Scenario: Delete playlist
- **WHEN** a client deletes a playlist
- **THEN** the playlist is removed while the referenced registry tracks remain intact

### Requirement: Playback mode is configured per playlist
The system SHALL store a playback mode (`sequential` or `random`) on each playlist, defaulting to `sequential` for new playlists, and SHALL remember user alterations.

#### Scenario: New playlist gets default mode
- **WHEN** a playlist is created without an explicit mode
- **THEN** its playback mode is sequential

#### Scenario: Mode change is remembered
- **WHEN** a client changes a playlist's playback mode and the application later restarts
- **THEN** the playlist retains the altered mode
