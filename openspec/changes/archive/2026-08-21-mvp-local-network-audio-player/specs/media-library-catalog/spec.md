## ADDED Requirements

### Requirement: Catalog scanner indexes supported music files
The system SHALL recursively scan the mounted `/music` directory at startup and index audio files with extensions `.flac`, `.mp3`, and `.ogg`.

#### Scenario: Startup scan discovers nested files
- **WHEN** the application starts with supported files in nested subdirectories under `/music`
- **THEN** the catalog includes every discovered supported file path

#### Scenario: Unsupported file types are ignored
- **WHEN** the scanner encounters files that are not `.flac`, `.mp3`, or `.ogg`
- **THEN** those files are excluded from the catalog without failing startup

### Requirement: Catalog exposes MVP track metadata
The system MUST provide per-track metadata containing filename, detected audio format, and duration when available.

#### Scenario: Metadata is available for a track
- **WHEN** a client requests track details for an indexed track with readable metadata
- **THEN** the response includes filename, format, and duration

#### Scenario: Duration cannot be determined
- **WHEN** metadata extraction cannot determine duration for an indexed file
- **THEN** the track remains available and duration is returned as unknown/null with a warning log entry

