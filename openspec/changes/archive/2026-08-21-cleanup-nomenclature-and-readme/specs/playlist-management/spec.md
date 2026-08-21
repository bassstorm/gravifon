## MODIFIED Requirements

### Requirement: MVP playlist view is read-only for client UX
The system MUST support playlist listing and active playlist selection, while playlist mutation operations may be unavailable.

#### Scenario: Client selects a playlist
- **WHEN** a client requests to set an available playlist as active
- **THEN** playback context updates to that playlist

#### Scenario: Client requests unsupported mutation
- **WHEN** a client calls create, update, or delete playlist operations that are not implemented
- **THEN** the API responds with `501 Not Implemented`
