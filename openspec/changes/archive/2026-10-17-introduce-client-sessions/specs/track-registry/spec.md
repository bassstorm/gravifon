## ADDED Requirements

### Requirement: Source registration is independent of playlists
The registry SHALL resolve source URLs and atomically create or reuse shared tracks, returning ordered track references. Registration MUST NOT create playlists or mutate playback; existing tracks retain their shared identity, metadata, and state.

#### Scenario: Reuse registration
- **WHEN** the same normalized source is registered again
- **THEN** existing registry IDs are returned without duplicate tracks or implicit playlist changes

#### Scenario: Resolution fails
- **WHEN** a source cannot be resolved or persistence fails
- **THEN** failure is explicit and no partial new registration is committed

#### Scenario: Later playlist insertion fails
- **WHEN** registration succeeds but a later playlist insertion fails
- **THEN** successfully registered shared tracks remain
