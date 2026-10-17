## MODIFIED Requirements

### Requirement: API access model is single-user and stateless
The system SHALL require persisted per-instance caller sessions for protected API requests, resolving unverified usernames to stable users without credentials or authenticated authorization. Acquisition and non-API endpoints SHALL remain accessible without a session.

#### Scenario: API call is made without authentication
- **WHEN** a client calls a protected API endpoint without a valid session
- **THEN** HTTP 401 with `SESSION_REQUIRED` is returned

#### Scenario: Multiple clients call API concurrently
- **WHEN** multiple clients acquire sessions
- **THEN** each resolves to its selected stable user without establishing authenticated ownership

## ADDED Requirements

### Requirement: API exposes session acquisition and caller invalidation
The API SHALL expose `POST /api/sessions` with username returning session/user identity and validity timestamps, and `DELETE /api/sessions/current` invalidating only the presented caller session.

#### Scenario: Acquire and invalidate
- **WHEN** a client posts a valid username
- **THEN** HTTP 201 returns `sessionId`, `userId`, `username`, `lastActivityAt`, and `expiresAt`
- **AND** deleting that current session returns 204 and subsequent protected requests with it return 401

### Requirement: API exposes independent shared stream registration
The API SHALL accept `POST /api/tracks/streams` with a nonempty `sourceUrls` list of nonblank strings and return ordered `trackIds` without creating playlists or altering playback. URL syntax and supported schemes SHALL be decided by the selected stream resolver.

#### Scenario: Source expands to tracks
- **WHEN** a valid caller registers a source resolving to multiple tracks
- **THEN** HTTP 200 returns their registry IDs in resolver order for subsequent playlist insertion

#### Scenario: Registration failure
- **WHEN** any source resolution or registration fails
- **THEN** the operation fails explicitly with no partial new registry entries
