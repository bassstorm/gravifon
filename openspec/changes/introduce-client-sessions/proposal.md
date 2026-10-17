# Proposal

## Why

The API is deliberately sessionless, so the server cannot distinguish player instances or resolve callers to owners of durable state. Username-based session acquisition establishes explicit user identity and a caller-resolution boundary without authentication, allowing the playback redesign to scope playlists, playback contexts, and player authority by user.

## What Changes

- Introduce a **client session**: one per player instance (such as a browser tab or app instance), identified by an opaque, server-minted `ClientSessionId`. A user can hold many concurrent client sessions; no additional player-instance identity is needed.
- Add explicit session acquisition with a client-supplied username (for example `POST /api/sessions`). Trim surrounding whitespace, reject blank names, and match case-insensitively. Resolve an existing user or create one with a stable server-issued user ID; username spelling is not the ownership key.
- **BREAKING** Require a valid client session for every API request, including audio streaming. Reject a missing, unknown, or invalidated session with an explicit "session required" outcome so the client can acquire a new session.
- Every API request presents `X-Client-Session`; audio GET/range requests also accept a media-only `session` query parameter. Conflicting identifiers are invalid. No shared cookie identity; client storage is deferred.
- Persist client sessions so a server restart does not invalidate them.
- Expire sessions through configurable inactivity renewed by ordinary valid requests. Expired/invalidated sessions cannot revive themselves; cleanup preserves users and their state and does not interrupt an already-open audio response.
- Persist users and associate each session with its resolved user. There is no implicit default user. The playback redesign makes playlists and playback contexts user-owned while retaining shared tracks and music sources.
- Acquiring a session has no playback side effects. Browsing and other reads stay side-effect-free.
- Expose `POST /api/tracks/streams` returning registry track IDs independently of playlist operations. Callers subsequently insert those IDs into user-owned playlists.
- Usernames are unverified identities, not credentials: anyone supplying a username can select that user's state. Authentication and authenticated authorization remain out of scope; user scoping partitions state but does not prove ownership.
- **BREAKING** Own the initial selective reset for the coordinated redesign: remove obsolete playlist/playback models, orchestration, routes, mappings, and exclusive tests while preserving the registry, stream delivery/refresh engines, health, observability, and applicable behavioral coverage. No old/new compatibility implementation is maintained.

## Capabilities

### New Capabilities

- `client-sessions`: Username-based acquisition and user resolution, stable user identity, per-instance session presentation and validation, persistence across restart, and the explicit session-required outcome.

### Modified Capabilities

- `player-rest-api`: Replace the single-user, stateless access model with username-based session acquisition and mandatory client sessions, without authenticated identity verification.
- `audio-streaming`: Require valid per-instance sessions for audio/range access.
- `track-registry`: Expose shared source registration returning track IDs independently of playlist membership.
- `agent-first-project-documentation`: Document unverified username selection, client sessions, user-owned state, and the absence of authentication.

## Impact

- Adds a session filter or interceptor in front of all controllers, a session acquisition endpoint, and a mapping of the session-required outcome to HTTP 401.
- Uses JPA/Flyway users and sessions, normalized-name uniqueness and track identity as backstops, admission-row serialization of user and shared-track creation, user-row serialization, MVC interception, and configurable 24-hour inactivity with hourly cleanup. Username limits, transaction boundaries, API shapes, and test targets are defined in Part 2.
- Future API clients must acquire a session and reacquire on session-required. Client implementation is deliberately deferred until the API/workflow settle; no client location or implementation is a blocker. API integration tests establish sessions through the public contract.
- Preserve existing `CorrelationId` logging; this change does not introduce optional raw session-ID logging. Playback owns revised operation-lifecycle terminology.
- Prerequisite for `redesign-playlist-and-playback-queue`, which implements user-owned playlists and playback contexts and a per-context active-player pointer to one of that user's sessions.
- Provides Phase 1 of a three-phase execution: session foundation/reset, then playlist/queue foundation, then playback protocol/integration. The latter two belong to the playback change. Each phase is green and behaviorally verifiable for human review, not necessarily an end-to-end working player.
- Temporarily removes playlist/playback functionality and proactive upcoming-track refresh integration until the playback change restores them. Request-time refresh and unaffected registry/streaming behavior remain. Temporary absence is execution scope, not a permanent removal requirement or grounds to weaken retained tests.
- Uses Spring Boot/MockMvc integration tests with real services/persistence as the outer verification boundary for normal-use workflows. Focused invariant tests remain; dedicated race/network/adversarial integration scenarios and browser verification are deferred.
- Out of scope: credentials, authenticated login, authenticated authorization, and choosing Spring Security. Future authenticated ownership claims and transport-specific protections need explicit design rather than assuming username selection proves identity.
- The design assigns requirement-delta ownership: this change owns session/media access and shared registration; the dependent playback change owns all playback-control deltas, playlist operations, byte-position removal, refresh coverage, and playback correlation terminology.

The design, owned specification deltas, and Phase 1 tasks form a complete planning handoff for review. Implementation is a separate workflow; tasks carry concrete scope, prerequisites, and verification without requiring conversation history.
