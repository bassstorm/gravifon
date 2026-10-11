## ADDED Requirements

### Requirement: Audio requests require valid instance sessions
Audio and range requests SHALL validate and renew the presented instance session at request initiation. Header or media-only query presentation MUST identify the same caller. Session tokens MUST NOT be exposed in diagnostics or error URLs.

#### Scenario: Valid range request
- **WHEN** a range request presents a valid instance session
- **THEN** ordinary range behavior applies and activity is renewed

#### Scenario: Session required before serving
- **WHEN** an audio request lacks a valid session
- **THEN** HTTP 401 is returned before serving bytes
