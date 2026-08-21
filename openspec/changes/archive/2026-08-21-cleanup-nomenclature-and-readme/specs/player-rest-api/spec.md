## MODIFIED Requirements

### Requirement: API contracts are future-client friendly
The system MUST use explicit resource identifiers and typed payloads that allow additional clients to consume the same API.

#### Scenario: Existing fields remain stable
- **WHEN** a new client integrates against the API
- **THEN** resource IDs and documented response fields remain consistent for supported operations

### Requirement: MVP API access model is single-user and stateless
The system MUST expose APIs for a single-user local-network scenario without authentication, authorization, or server-side session management.

#### Scenario: API call is made in MVP mode
- **WHEN** a client calls an API endpoint
- **THEN** the request is processed without authn/authz checks and without creating or requiring a server session

#### Scenario: Multiple clients call API concurrently in MVP
- **WHEN** more than one client issues requests at the same time
- **THEN** the API behavior remains globally consistent for shared server playback context and does not establish per-user playback/session state
