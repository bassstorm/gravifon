## MODIFIED Requirements

### Requirement: Registry scanner reconciles supported music files
The system SHALL recursively reconcile supported `.flac`, `.mp3`, and `.ogg` files under the music mount against the durable registry without rebuilding it. Background startup reconciliation SHALL not block availability. Missing files SHALL be tombstoned if any user's playlist or queue references them, otherwise removed; reference creation and removal MUST preserve this protection.

#### Scenario: Startup scan discovers nested files
- **WHEN** startup scan completes
- **THEN** nested supported files are registered

#### Scenario: Service is available before the startup scan completes
- **WHEN** background startup scan is in progress
- **THEN** requests use the persisted registry snapshot and unscanned tracks can be missing

#### Scenario: Unsupported file types are ignored
- **WHEN** a file has unsupported extension
- **THEN** it is excluded without failing startup

#### Scenario: Known file is re-scanned
- **WHEN** an existing source identity is rediscovered
- **THEN** metadata/state refresh in place preserving track identity

#### Scenario: Previously failing file becomes readable
- **WHEN** a previously unreadable file can be scanned
- **THEN** its read failure clears

#### Scenario: Referenced track's file is missing
- **WHEN** any user's playlist or current/retained/generated queue references a missing file-backed track
- **THEN** the track remains with source-missing diagnosis, including detached occurrences

#### Scenario: Unreferenced track's file is missing
- **WHEN** no user's playlist or queue references a missing file-backed track
- **THEN** its track and metadata are removed

#### Scenario: Missing file returns
- **WHEN** the same relative source returns
- **THEN** the track resumes under its original identity
