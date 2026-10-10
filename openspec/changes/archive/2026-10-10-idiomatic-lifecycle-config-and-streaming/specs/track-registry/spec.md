# Spec Delta

## MODIFIED Requirements

### Requirement: Registry scanner reconciles supported music files
The system SHALL recursively scan the mounted `/music` directory and reconcile discovered audio files with extensions `.flac`, `.mp3`, and `.ogg` against the persistent track registry, rather than rebuilding the registry from scratch. The startup scan MUST run as a background reconciliation that does not block service startup, with the service handling requests against the persisted registry snapshot while the scan is in progress.

#### Scenario: Startup scan discovers nested files
- **WHEN** the application starts with supported files in nested subdirectories under `/music`
- **THEN** once the background startup scan completes, every discovered supported file is present in the registry as a file-backed track

#### Scenario: Service is available before the startup scan completes
- **WHEN** the application has started but the background startup scan is still in progress
- **THEN** requests are served against the persisted registry snapshot, and tracks not yet reconciled may be reported as missing

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
