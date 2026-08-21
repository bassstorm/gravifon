*Scope note: this iteration is server-only. The `mvp-web-client` capability and its tasks are removed from this change; a follow-up change will propose the browser client once this server contract is validated.*

## 1. Foundation and configuration

- [x] 1.1 Confirm Java 21/Spring Boot/Maven baseline and align dependency versions for MVP metadata parsing and streaming support.
- [x] 1.2 Add configuration properties for music root path (`/music` default), startup scan options, and profile-specific logging behavior.
- [x] 1.3 Add shared API error model and request-tracing contract for `CorrelationId` passthrough and server-generated fallback.

## 2. Media catalog domain

- [x] 2.1 Implement recursive startup scanner for `/music` filtering supported extensions (`.flac`, `.mp3`, `.ogg`).
- [x] 2.2 Implement track model enrichment for filename, format, and duration with graceful fallback when duration is unavailable.
- [x] 2.5 Add `jaudiotagger` dependency and duration extraction adapter with safe fallback behavior.
- [x] 2.3 Add catalog query service methods for listing tracks and resolving track by id/path for API and streaming layers.
- [x] 2.4 Add unit tests for scan recursion, unsupported file filtering, and metadata fallback behavior.

## 3. Playlist domain (in-memory)

- [x] 3.1 Implement in-memory playlist repository/service lifecycle rebuilt on startup.
- [x] 3.2 Implement default all-tracks playlist bootstrap and selection semantics.
- [x] 3.3 Implement playlist read/list/select endpoints and explicit MVP responses for unsupported mutations; selecting a playlist eagerly re-picks a current track per the active mode (no lazy normalize-on-read).
- [x] 3.4 Add tests for default playlist creation, empty-catalog behavior, playlist selection flow, and eager re-pick on playlist switch.

## 4. Playback domain

- [x] 4.1 Implement server-maintained playback context model (active playlist, current track, mode, transport state, position), replacing the earlier client-managed-transport model.
- [x] 4.2 Implement playback mode handling for sequential and random next-track selection.
- [x] 4.5 Ensure random mode remains pure random per step (no shuffle-without-repeat cycle semantics).
- [x] 4.3 Implement entity-reference-driven playback commands: explicit track selection, server-driven next-track advance, mode update, and transport update (`playing`/`paused`/`stopped`, with `stopped` resetting position to zero). Remove the legacy `/play`, `/pause`, `/stop`, `/seek*` endpoints.
- [x] 4.6 Implement playback position tracking: an optional, explicit client report (track-id guarded, ignored when stale or when it implies playback beyond what has been streamed) with a passive fallback derived from observed streaming activity for the current track.
- [x] 4.4 Add tests for transport transitions, position report/fallback/plausibility-clamp behavior, entity-reference commands, and next-track behavior in both playback modes.

## 5. Streaming API

- [x] 5.1 Implement streaming endpoint that resolves catalog track resources and serves original bytes without transcoding.
- [x] 5.2 Implement HTTP range parsing and partial-content responses (`206`) for browser seek/buffer compatibility.
- [x] 5.3 Implement standards-compliant handling for invalid ranges (`416`) and missing tracks (`404`).
- [x] 5.4 Extend range/streaming tests to cover closed and open-ended ranges and error conditions.
- [x] 5.5 Ensure streaming stays free of authoritative playback-state side effects: only observe byte ranges for the current track id as a passive position fallback signal, never override an explicit plausible position report, and never mutate state for non-current-track requests (e.g., prefetch/caching).

## 6. REST API integration

- [x] 6.1 Finalize entity-reference-driven endpoint contracts for tracks, playlists, and playback (playlist select, track select, mode, transport, next, optional position report), removing legacy `/play`, `/pause`, `/stop`, `/seek*` endpoints, with DTO consistency.
- [x] 6.2 Wire controllers to domain services with validation, typed responses, and stable resource identifiers; keep data-retrieval endpoints side-effect free and state-manipulation endpoints entity-reference driven.
- [x] 6.3 Add API integration tests verifying reconnect-restore behavior, transport/position semantics, eager re-pick on playlist switch, and that the contract makes no client-architecture assumptions.

## 7. Observability

- [x] 7.1 Implement request filter/interceptor to propagate incoming `CorrelationId` or generate one when missing, log a warning, and include it in MDC/response logs.
- [x] 7.2 Configure structured logging as default profile behavior and plain text logging for local/dev profile.
- [x] 7.4 Add tests for correlation id passthrough, missing-header fallback generation, and request-log propagation.
- [x] 7.5 Implement and document severity-level conventions (`trace`, `debug`, `info`, `warn`, `error`) for both client and server logs.
- [x] 7.6 Implement active-lifecycle correlation scope rules, including boundaries on track transition/replacement, same-track replay, playlist switch, and playback mode change.

## 8. Testing quality gates

- [x] 8.1 Define and document coverage scope so business functionality exceeds 80 percent unit-test coverage.
- [x] 8.2 Implement tests with AssertJ, Mockito, and Spring/Spring Boot testing support as default stack.
- [x] 8.3 Add coverage reporting/check step and fail verification if coverage target is not met.
- [x] 8.4 Keep boilerplate low by using Java 21 features, Lombok, Apache Commons, and Spring utilities where they improve clarity.

## 9. Docker packaging and documentation

- [x] 9.1 Create or refine Docker image build for the Spring Boot server (existing static assets remain packaged as-is; not actively developed this iteration).
- [x] 9.2 Create `docker-compose.yaml` (or refine if present) for local execution with default host mount `~/Library:/music`.
- [x] 9.3 Document standalone container run steps for NAS deployment (ports, volumes, env vars).
- [x] 9.4 Update `AGENTS.md`, OpenSpec config docs, and README architecture/testing guidance for agent-first development.
- [x] 9.5 Document and verify explicit MVP access scope: single-user usage, no authn/authz, and no session management.

## 10. Clean verification workflow

- [x] 10.1 Run backend validation with clean lifecycle commands (`mvn clean test`, then `mvn clean verify` when needed).
- [x] 10.2 Reset containerized state with `docker compose down -v` before environment-sensitive verification passes.

## 11. Review follow-ups (post-implementation review)

- [x] 11.1 Decide and document the observed-position fallback update rule for backward seeks: keep monotonic max (immune to stray probe ranges, but the fallback can't move backward without an explicit client report) vs. switch to "most recent observed range" (correct on backward seeks, more exposed to out-of-order/probe range requests). Record the decision and rationale in `design.md`.
- [x] 11.2 Update `PlaybackService.observeStream` to match the 11.1 decision; resolve the currently unreachable branch that nulls `reportedPositionSeconds` when it exceeds `observedPositionSeconds` (remove it if still dead, or keep it purposefully wired if 11.1 allows `observedPositionSeconds` to decrease).
- [x] 11.3 Add a `PlaybackServiceTest` case exercising `selectPlaylist` with two distinct mocked playlists, asserting the current track changes to one from the newly active playlist (eager re-pick on playlist switch has no direct test today).
- [x] 11.4 Add a `PlaybackServiceTest` case proving an explicit, plausible position report is preserved after subsequent `observeStream` activity for the same track (the "streaming never overrides an explicit report" scenario is implemented but untested).
- [x] 11.5 Add a `PlaybackControllerWebMvcTest` case for an invalid `transport` path value returning 400 (mirrors the existing invalid-mode test), and a case asserting `selectTrack` for a track outside the active playlist surfaces as HTTP 400 end-to-end.
