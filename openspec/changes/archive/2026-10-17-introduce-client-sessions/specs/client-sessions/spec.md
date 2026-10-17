## Purpose

Define unverified username resolution and durable per-instance caller sessions independently of playback ownership and authority.

## ADDED Requirements

### Requirement: Acquisition resolves a stable unverified user
The system SHALL acquire a session from a username without credentials, trim surrounding whitespace, accept 1-64 ASCII letters/digits/dot/underscore/hyphen, and match case-insensitively. Equivalent names MUST resolve to one stable server-issued user ID with first-seen display spelling.

#### Scenario: Equivalent names select the same user
- **WHEN** clients acquire using `Alice` and ` alice `
- **THEN** their distinct sessions reference the same user and display `Alice`

#### Scenario: Concurrent first acquisitions of equivalent names
- **WHEN** two clients acquire `Alice` and ` alice ` at the same time and no user yet exists for that name
- **THEN** both acquisitions succeed, both sessions reference the same single user, and neither fails on a username uniqueness conflict

#### Scenario: Invalid username
- **WHEN** acquisition supplies a blank, oversized, or unsupported username
- **THEN** it fails explicitly without creating a user or session

#### Scenario: Usernames are not authenticated claims
- **WHEN** any client supplies an existing valid username
- **THEN** it can select that user without proof of ownership

### Requirement: Sessions identify individual caller instances
The system SHALL issue opaque session IDs independently for each acquisition, persist their user association, and preserve valid identity across server restarts. Sessions MUST NOT collapse browser tabs into a shared identity.

#### Scenario: Multiple instances of one user
- **WHEN** two instances acquire for the same username
- **THEN** they receive different session IDs associated with the same stable user

#### Scenario: Restart preserves validity timing
- **WHEN** the server restarts
- **THEN** persisted session identity and activity timing remain unchanged and expired sessions are not revived

### Requirement: Acquisition has no playback side effects
Acquisition SHALL NOT take over, create playlists, alter playback intent/queue/bookmark, invalidate other sessions, or replay uncertain commands.

#### Scenario: Reacquisition
- **WHEN** a client acquires again, including after an unanswered acquisition
- **THEN** a new session is created without changing existing sessions or playback

### Requirement: API presentation preserves caller identity
Protected API requests MUST present `X-Client-Session`. Audio GET/range requests SHALL additionally support `session` query presentation. Conflicting or duplicate identifiers MUST fail as invalid input; missing, unknown, expired, or invalidated sessions MUST produce session-required.

#### Scenario: Media-only presentation
- **WHEN** an audio range GET presents its session through the query parameter
- **THEN** it resolves the same caller as header presentation

#### Scenario: Conflicting presentation
- **WHEN** header and media query identify different sessions, or session query presentation occurs on another API route
- **THEN** the request fails explicitly with HTTP 400

#### Scenario: Invalid session
- **WHEN** a protected request presents no valid session
- **THEN** HTTP 401 with code `SESSION_REQUIRED` is returned with request correlation and without exposing the rejected token

### Requirement: Inactivity controls session validity
Valid request initiation SHALL renew session activity. Sessions SHALL expire at the configured inactivity boundary, default 24 hours. Invalidated/expired sessions MUST NOT renew themselves. No heartbeat or acquisition-age lifetime is required.

#### Scenario: Ordinary requests renew
- **WHEN** a valid session initiates an API or audio/range request
- **THEN** activity is renewed even if downstream input validation rejects that operation

#### Scenario: Inactivity boundary
- **WHEN** the inactivity period has elapsed since last activity
- **THEN** the next request requires a new session, independently of cleanup timing

### Requirement: Invalidation and cleanup preserve owned state
Explicit invalidation SHALL invalidate only the caller session. Expiration/invalidation MUST clear a matching active-player reference without changing playback facts. Cleanup SHALL delete only eligible session records, preserving users and their state.

#### Scenario: Invalidate caller
- **WHEN** a valid caller deletes its current session
- **THEN** that session cannot be reused and another session of the same user remains valid

#### Scenario: Clear matching authority
- **WHEN** an active session expires or is invalidated
- **THEN** its active-player reference is cleared while queue, intent, bookmark, instruction and last observation remain

#### Scenario: Delayed cleanup
- **WHEN** an old invalid session is cleaned after another session took over
- **THEN** the newer active-player reference is not cleared

### Requirement: Open audio responses are not session heartbeats
An already-open audio response SHALL NOT be interrupted solely by later session expiration. Delivered bytes MUST NOT renew activity; new requests MUST validate normally.

#### Scenario: Audio response spans expiration
- **WHEN** a valid request began serving audio and its session later expires
- **THEN** that response is not terminated solely for expiration, but subsequent requests require a valid session
