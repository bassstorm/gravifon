# Track Registry Specification

## Purpose
Define the unified, persistent, metadata-bearing registry for all tracks — file-backed and stream-backed — including identity, metadata, failure state, and scan reconciliation.

## ADDED Requirements

### Requirement: Registry scanner reconciles supported music files
The system SHALL recursively scan the mounted `/music` directory at startup and reconcile discovered audio files with extensions `.flac`, `.mp3`, and `.ogg` against the persistent track registry, rather than rebuilding the registry from scratch.

#### Scenario: Startup scan discovers nested files
- **WHEN** the application starts with supported files in nested subdirectories under `/music`
- **THEN** every discovered supported file is present in the registry as a file-backed track

#### Scenario: Unsupported file types are ignored
- **WHEN** the scanner encounters files that are not `.flac`, `.mp3`, or `.ogg`
- **THEN** those files are excluded from the registry without failing startup

#### Scenario: Known file is re-scanned
- **WHEN** a file is discovered whose identity matches an existing registry track
- **THEN** the track's metadata and state are refreshed from disk in place, preserving the track's identity

#### Scenario: Previously failing file becomes readable
- **WHEN** a re-scan can read a file whose track was previously marked failing with a read error
- **THEN** the track's error state is cleared

#### Scenario: Referenced track's file is missing
- **WHEN** a scan completes and a file-backed track referenced by at least one playlist has no corresponding file under `/music`
- **THEN** the track is retained and marked with a source-missing error state instead of being deleted

#### Scenario: Unreferenced track's file is missing
- **WHEN** a scan completes and a file-backed track referenced by no playlist has no corresponding file under `/music`
- **THEN** the track and its metadata are removed from the registry

#### Scenario: Missing file returns
- **WHEN** a later scan rediscovers a file at the same location as a source-missing track
- **THEN** the track resumes normal state under its original identity

### Requirement: Registry exposes track metadata
The system MUST provide per-track metadata as a multi-value map of metadata keys (well-known keys including artist, album, title, album artist, date, track number, track total) and MUST admit additional non-standard metadata keys.

#### Scenario: Metadata is available for a track
- **WHEN** a client requests track details for a registered track
- **THEN** the response includes its metadata map

#### Scenario: Multi-value metadata is preserved
- **WHEN** a track carries multiple values for one metadata key (e.g. several genres)
- **THEN** all values are exposed in their defined order

### Requirement: Duration is a first-class track attribute
The system SHALL maintain a typed duration for every track, populated from file metadata at scan time for file-backed tracks and from integration-supplied data for stream-backed tracks, and exposed uniformly regardless of track kind.

#### Scenario: Duration is available for a track
- **WHEN** a client requests track details for a track with known duration
- **THEN** the response includes the duration in the same field for file-backed and stream-backed tracks

#### Scenario: Duration cannot be determined
- **WHEN** duration extraction fails for an indexed file or is unknown for a stream track
- **THEN** the track remains available and duration is returned as unknown/null with a warning log entry

### Requirement: Registry is the unified persistent track store
The system SHALL persist all tracks — file-backed and stream-backed — in a single durable registry that survives application restarts, and SHALL treat the registry as the only metadata-bearing track store in the system.

#### Scenario: Registry survives restart
- **WHEN** the application restarts
- **THEN** previously registered tracks (file and stream), their metadata, and their state are available without re-adding stream tracks or losing user metadata edits

#### Scenario: File track identity is stable across remounts
- **WHEN** the music library is mounted at a different absolute path but file locations relative to the library root are unchanged
- **THEN** file-backed track identities remain stable

### Requirement: Track identity is derived from source identity
The system SHALL derive track identity deterministically from source identity: file-backed tracks from the file path relative to the music root, and stream-backed tracks from a normalized form of the stream source URL excluding volatile parameters.

#### Scenario: Re-adding a known stream source
- **WHEN** a stream source URL is added that normalizes to the identity of an existing stream track
- **THEN** the existing track is reused rather than duplicated

#### Scenario: Stream URLs differing only in volatile parameters
- **WHEN** two added stream source URLs differ only in volatile query parameters
- **THEN** they resolve to the same track identity

### Requirement: Stream tracks are first-class registry entries
The system MUST support stream-backed tracks carrying a stable source URL, a volatile stream URL with an optional expiration timestamp, full metadata, and per-track state, created independently of the filesystem scan.

#### Scenario: Stream tracks are added from a source URL
- **WHEN** a client adds one or more source URLs that a stream resolver expands into one or more tracks
- **THEN** a stream-backed registry track is created or reused for each resolved track with its supplied metadata, and each can be referenced by playlists like any file-backed track

#### Scenario: One source URL expands to multiple tracks
- **WHEN** a single added source URL represents a collection (e.g. an album page)
- **THEN** one registry track is created or reused per resolved track in the collection

### Requirement: Tracks carry structured failure state
The system MUST maintain per-track failure state consisting of a failing flag and an optional structured last error (kind, message, timestamp, reporter), settable by the scan, by stream refresh, and by client report.

#### Scenario: Scan encounters an unreadable file
- **WHEN** metadata extraction for a file fails during scan
- **THEN** the track is retained, marked failing with a read-error state attributed to the scan, and remains listed

#### Scenario: Failure state is exposed to clients
- **WHEN** a client requests track details for a failing track
- **THEN** the response includes the failing flag and the last error details
