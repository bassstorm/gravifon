## MODIFIED Requirements

### Requirement: Playlists are persisted and survive restarts
The system SHALL persist each user-owned playlist's name, stable entry IDs, ordered shared-track references, and mode preference. No playlist SHALL be created implicitly by acquisition or playback reads.

#### Scenario: Restart preserves playlists
- **WHEN** stored playlists are reloaded after restart
- **THEN** owner, name, identities, order, references and preferences remain

#### Scenario: Fresh install has no playlists
- **WHEN** a user has not explicitly created a playlist
- **THEN** none exists for that user

### Requirement: Playlist entries reference shared registry tracks
Playlist entries SHALL have stable identities independent of track and order, referencing shared registry tracks without copying metadata/state. Duplicate tracks SHALL remain distinct entries.

#### Scenario: Metadata edit propagates to all playlists
- **WHEN** shared metadata changes
- **THEN** every referencing playlist across users observes the same track metadata

#### Scenario: Failure state is visible across playlists
- **WHEN** a shared track is marked failing
- **THEN** the same state is visible wherever referenced

#### Scenario: Track appears multiple times
- **WHEN** the same track is inserted twice
- **THEN** distinct entry IDs reference that one registry track

### Requirement: Clients can create and mutate playlists
The system SHALL support user-scoped creation from track IDs or catalog materialization, rename/preference changes, insertion, entry-ID removal, strict reorder and deletion. Source registration SHALL be separate. Mutations MUST preserve unaffected identities and atomically reconcile the owning playback context.

#### Scenario: Create playlist from track ids
- **WHEN** a caller creates with registered track IDs
- **THEN** entries persist in supplied order with fresh entry IDs

#### Scenario: Create playlist including stream links
- **WHEN** a caller inserts IDs returned by registry source registration
- **THEN** ordinary playlist insertion applies without resolving source URLs again

#### Scenario: Materialize catalog as playlist
- **WHEN** catalog materialization is requested
- **THEN** a user-owned snapshot ordered by track ID is created and not auto-updated by later registry changes

#### Scenario: Reorder entries
- **WHEN** the caller supplies the complete existing entry-ID permutation
- **THEN** order changes without membership or identity changes
- **AND** omissions, duplicates or unknown IDs fail without partial mutation

#### Scenario: Delete playlist
- **WHEN** the user deletes a playlist
- **THEN** registry tracks remain and playback associations reconcile without stopping protected current playback

#### Scenario: Foreign playlist
- **WHEN** a caller addresses a playlist owned by another user
- **THEN** it is unavailable in that caller's scope and no mutation occurs

### Requirement: Playback mode is configured per playlist
The system SHALL store `SEQUENTIAL` or `RANDOM` preference per playlist, using the configured default when creation omits it (shipped default `SEQUENTIAL`), and change it only through playlist mutation. Playback SHALL interpret that preference without a second authoritative mode copy.

#### Scenario: New playlist gets default mode
- **WHEN** creation omits preference
- **THEN** the configured default is used, which is sequential in the shipped configuration

#### Scenario: Mode change is remembered
- **WHEN** mode changes
- **THEN** the preference persists and bound source choices reconcile without replacing current or starting a new instruction
