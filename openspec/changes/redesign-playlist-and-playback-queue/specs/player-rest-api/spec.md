## MODIFIED Requirements

### Requirement: API exposes domain endpoints for tracks, playlists, and playback
The API SHALL expose shared track listing/details/metadata/state, user-owned playlist operations, read-only canonical playback snapshots and dedicated typed playback writes. Initialization and track-addressed playback selection SHALL be replaced by entry-addressed explicit play.

#### Scenario: Client retrieves playback state
- **WHEN** a valid caller gets `/api/playback`
- **THEN** the response contains user/revision, queue segments/source, intent, bookmark, instruction, execution and active-player projection

#### Scenario: Client reports transport transitions explicitly
- **WHEN** a client reports execution for current occurrence/instruction
- **THEN** the server records execution evidence separately from requested transport intent

#### Scenario: Client lists tracks
- **WHEN** a valid caller lists tracks
- **THEN** summaries cover the shared registry with stable identifiers

#### Scenario: Client retrieves track details
- **WHEN** a valid caller retrieves track details
- **THEN** kind, metadata, advisory duration, failure state and stream source/expiration are exposed without volatile stream URL

### Requirement: API supports playlist mutation
The API SHALL support owned playlist create/catalog, name/preference update, track-ID insert-before/append, entry-ID removal, full-permutation reorder and delete. Mutations SHALL reconcile context atomically and return playlist data without an active flag. Source registration SHALL remain a separate registry operation.

#### Scenario: Client creates a playlist
- **WHEN** the caller posts name and ordered track IDs
- **THEN** HTTP 201 returns stable entry IDs in caller order

#### Scenario: Source URL resolves to multiple tracks
- **WHEN** registry registration returned multiple IDs and the caller inserts them
- **THEN** ordinary insertion creates entries without another source-resolution operation

#### Scenario: Client mutates a playlist
- **WHEN** a valid owner renames, inserts/removes/reorders entries or changes mode
- **THEN** the update and context reconciliation commit together and HTTP 200 returns playlist representation

#### Scenario: Mutation references unknown track
- **WHEN** a mutation references missing track/entry/anchor
- **THEN** HTTP 404 is returned without partial mutation

### Requirement: API surface separates data retrieval from state manipulation
Data retrieval SHALL not select, advance, reroll, take over or alter playback intent/bookmark. Session renewal and detected-expiration authority cleanup are lifecycle maintenance only. Playlist mutations return playlist data; playback writes return canonical snapshots and explicit outcomes.

#### Scenario: Data-retrieval endpoint has no side effects
- **WHEN** tracks/playlists/playback/audio are read
- **THEN** no playback transition occurs, apart from explicitly documented session lifecycle maintenance

#### Scenario: State-manipulation endpoint is entity-reference driven
- **WHEN** playback is commanded
- **THEN** targets are entry/occurrence/instruction references rather than client selection algorithms or whole-snapshot equality

#### Scenario: Mode and transport state commands accept valid enum values
- **WHEN** a caller updates playlist `mode` or reports typed execution
- **THEN** exact uppercase canonical enum values are accepted

#### Scenario: Mode and transport state commands reject invalid enum values
- **WHEN** a payload supplies an unknown enum name
- **THEN** HTTP 400 is returned without changing playback state

### Requirement: API is agnostic to client playback architecture
The API SHALL use domain identities and typed operations independently of client decoder, buffering, or fetch cadence. Execution/position reports SHALL identify occurrence and originating instruction, not merely track.

#### Scenario: Alternate client architecture requires no API changes
- **WHEN** a custom decode/buffering client integrates
- **THEN** it can use the same session, command, observation, and audio contracts

#### Scenario: Position reporting does not assume a fetch cadence
- **WHEN** a caller reports integer-millisecond position
- **THEN** applicability depends on occurrence/instruction, not fetched byte count

### Requirement: API validates request payloads declaratively
The API SHALL validate mutation/report payload structure and nested elements before application logic, returning standard 400 responses. Application boundaries SHALL separately enforce domain/operation invariants, rejecting invalid direct calls explicitly.

#### Scenario: Missing or blank required field
- **WHEN** creation has a missing/blank playlist name
- **THEN** HTTP 400 identifies invalid input

#### Scenario: Malformed request payload
- **WHEN** JSON, enums, or required fields are malformed
- **THEN** HTTP 400 is returned

#### Scenario: Invalid position report
- **WHEN** positionMs is negative or not representable as a signed 64-bit integer
- **THEN** the observation/checkpoint fails with 400 without partial mutation

## ADDED Requirements

### Requirement: Dedicated playback writes return explicit outcomes
Playback SHALL expose `/play-from-entry`, `/play`, `/pause`, `/stop`, `/next`, `/retry`, `/advance`, and `/observations` POST routes. Commands use sequence/expected state/checkpoint and permitted takeover; observations never take over.

#### Scenario: Applied command
- **WHEN** a valid command applies
- **THEN** HTTP 200 returns `{outcome:APPLIED,snapshot}`

#### Scenario: Recognized retry
- **WHEN** identical sequence/content was already applied
- **THEN** HTTP 200 returns `ALREADY_APPLIED` and current snapshot without execution

#### Scenario: Not applied or conflict
- **WHEN** sequence/authority/report is superseded or eligible command preconditions conflict
- **THEN** HTTP 409 returns `NOT_APPLIED` or `CONFLICT`, reason and snapshot

#### Scenario: Missing or invalid contract
- **WHEN** the request is invalid, session-required, or references a missing/foreign resource
- **THEN** standard error response uses 400, 401 or 404 respectively

### Requirement: Expected playback state is explicit and relevant
Commands SHALL carry explicit expected current occurrence, instruction and intent, including absent-current state. They MUST NOT use snapshot revision as a precondition. Attached checkpoints SHALL identify the preceding instruction and validate atomically.

#### Scenario: Pause preserves checkpoint
- **WHEN** a valid Pause includes a final checkpoint
- **THEN** its bookmark is applied with Pause in one operation

#### Scenario: Superseded attached evidence
- **WHEN** an attached checkpoint no longer matches current playback
- **THEN** the whole command conflicts; evidence is not ignored to apply another action
