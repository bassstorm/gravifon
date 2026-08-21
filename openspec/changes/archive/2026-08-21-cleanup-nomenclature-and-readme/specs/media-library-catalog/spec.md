## MODIFIED Requirements

### Requirement: Catalog exposes MVP track metadata
The system MUST provide per-track metadata containing filename, detected audio format, and duration when available.

#### Scenario: Metadata is available for a track
- **WHEN** a client requests track details for an indexed track with readable metadata
- **THEN** the response includes filename, format, and duration

#### Scenario: Duration cannot be determined
- **WHEN** metadata extraction cannot determine duration for an indexed file
- **THEN** the track remains available and duration is returned as unknown/null with a warning log entry
