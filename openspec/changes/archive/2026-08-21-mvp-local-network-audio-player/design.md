## Context

Gravifon is a local-network audio player targeting containerized deployment where a host music directory is mounted into the app at runtime. The MVP must provide server-maintained playlist/track/mode/transport/position context, expose REST APIs for current and future clients that stay agnostic to any specific client's playback architecture, and stream raw audio without transcoding. The platform constraints are Java 21, Spring Boot, Maven, and Docker/Compose. This iteration is server-only: the browser client is deferred to a dedicated follow-up change, proposed once this server contract has proven itself through unit and Spring integration testing. State persistence is out of scope for MVP; all operational state resets on process restart (client reconnects within a running process are restored from the same in-memory context).

## Goals / Non-Goals

**Goals:**
- Implement a clean client-server architecture where the backend controls all playback context (mode, selected playlist, current track, transport state, position) and exposes it through a REST contract that assumes nothing about a specific client's audio pipeline or buffering strategy.
- Build a media catalog scanner for `/music` that indexes `.flac`, `.mp3`, and `.ogg` files from nested folders.
- Ensure at least one default playlist exists (all tracks) even when no playlist files are present.
- Expose stable, entity-reference-driven REST endpoints for playback control, playlist selection, track and playlist resources, and audio streaming.
- Provide audio streaming with byte-range support and no transcoding, suitable for any range-request-capable playback client.
- Add correlation-aware, structured server logs for troubleshooting.
- Package the application as a Docker image with Compose-friendly runtime configuration.
- Provide project guidance docs that support agent-first development.

**Non-Goals:**
- Persistent playback or playlist state across process restarts (an in-process reconnect, such as a client closing and reopening a browser tab while the server keeps running, is served from the same in-memory context and is explicitly in scope; see Decision 14).
- User authentication/authorization and multi-tenant access controls.
- Session management (server-side sessions, login state, or identity-bound session lifecycle).
- Multi-user concurrency controls; MVP is intentionally single-user scoped.
- Any browser/web client implementation, including the `mvp-web-client` capability; this is a server-only iteration, and the client is deferred to a dedicated follow-up change.
- Playlist editing UX.
- Advanced metadata enrichment (album art, tags normalization, lyrics, waveform generation).
- Transcoding, loudness normalization, or adaptive bitrate streaming.

## Decisions

### 1) Single-process server-maintained playback context domain
- **Decision:** Keep playback context orchestration in backend services (`catalog`, `playlist`, `playback`, `streaming`) and expose snapshots to clients, including transport state (see Decision 14), so any reconnecting client gets a complete, consistent snapshot without server-side sessions.
- **Rationale:** This keeps shared state deterministic across clients and makes playback context fully restorable across client reconnects.
- **Alternatives considered:**
  - Fully client-authoritative state (playlist/track/mode/position/transport): rejected due to drift across clients and weaker API contract.
  - External message broker/state store: rejected for MVP complexity.

### 2) In-memory runtime model with startup scan
- **Decision:** Scan `/music` at startup, build in-memory catalog/playlist model, and do not persist operational state.
- **Rationale:** Aligns with MVP speed and explicit no-persistence requirement.
- **Alternatives considered:**
  - Persistent DB-backed model: deferred to later phases.
  - On-demand filesystem traversal per API request: rejected due to latency and inconsistent snapshots.

### 3) Capability-oriented REST surface with future-safe shape
- **Decision:** Separate endpoints by domain: playback control/state, playlists, tracks, streaming.
- **Rationale:** Keeps API extensible for richer clients while preserving clear bounded contexts.
- **Alternatives considered:**
  - Monolithic `/player/*` endpoint: rejected as too rigid for future scenarios.

### 4) Range-based raw audio streaming
- **Decision:** Implement HTTP range request handling for track resources and stream source bytes directly.
- **Rationale:** Range-request-capable playback clients rely on standard HTTP range semantics for seek and buffering, regardless of their internal decode/buffering strategy.
- **Alternatives considered:**
  - Full-file responses only: rejected due to poor seek behavior.
  - On-the-fly transcoding: explicitly out of scope.

### 5) CorrelationId propagation via request filter and logging context
- **Decision:** Accept optional `CorrelationId`; when missing, generate one server-side, log a warning, store it in MDC, and include it in all request-scoped logs.
- **Rationale:** Preserves traceability while keeping clients resilient during early MVP integration.
- **Alternatives considered:**
  - Reject missing header with `400`: rejected for MVP ergonomics.

### 6) Profile-aware logging output format
- **Decision:** Use structured logging by default; use human-readable text logs for local/dev profile.
- **Rationale:** Matches operational needs and local developer ergonomics.
- **Alternatives considered:**
  - Structured-only logs: rejected for local readability.

### 7) Web client deferred to a follow-up change
- **Decision:** Do not implement or decide packaging for a browser client in this change. Once the server contract is validated through this iteration's testing, propose a dedicated follow-up change (`mvp-web-client` or similar) that revisits how the client is served (bundled in the same image vs. separate) against the settled contract.
- **Rationale:** Deciding client packaging now, before the API has stabilized through real testing, risks locking in choices that don't fit the eventual contract; splitting the change reduces rework and keeps this change's blast radius to the server domain.
- **Alternatives considered:**
  - Decide and build client packaging alongside this change: rejected because it re-couples client work to an API that is still actively being refined.

### 8) MVP random mode remains pure random
- **Decision:** Implement random playback as independent random selection per next-track step.
- **Rationale:** Keeps complexity low and aligns with intended MVP scope.
- **Alternatives considered:**
  - Shuffle-without-repeat cycle: deferred as a future dedicated playback mode.

### 9) Track duration extraction uses jaudiotagger
- **Decision:** Use `jaudiotagger` for duration metadata extraction with graceful unknown fallback.
- **Rationale:** Mature library with known behavior for expected formats.
- **Alternatives considered:**
  - Java Sound only: rejected due to limited/uneven support for target formats.

### 10) Explicit MVP mutation contract for playlists
- **Decision:** Return `501 Not Implemented` for unsupported playlist mutation endpoints.
- **Rationale:** Makes MVP limitations explicit and machine-detectable.
- **Alternatives considered:**
  - `405 Method Not Allowed`: rejected because endpoints may exist but be intentionally unimplemented in MVP.

### 11) Engineering conventions for implementation phase
- **Decision:** Target more than 80 percent unit-test coverage for business functionality; use AssertJ, Mockito, and Spring/Spring Boot testing features; keep implementation lean using Java 21 features plus Lombok, Apache Commons, and Spring utilities where appropriate.
- **Rationale:** Improves maintainability and delivery speed while minimizing boilerplate.
- **Alternatives considered:**
  - Lower coverage target: rejected because it weakens confidence during rapid MVP iteration.

### 12) Shared log-level semantics across server and client
- **Decision:** Enforce common severity semantics: `trace` high-detail troubleshooting, `debug` diagnostics, `info` key lifecycle events, `warn` recoverable issue-like conditions, `error` actual failures.
- **Rationale:** Prevents noisy/ambiguous observability and makes triage predictable.
- **Alternatives considered:**
  - Team-specific ad hoc level usage: rejected due to inconsistent troubleshooting outcomes.

### 13) CorrelationId scope equals one active operation/resource lifecycle
- **Decision:** Use one correlation id per active lifecycle: track playback lifecycle, playlist switch lifecycle, or playback-mode change lifecycle. Keep the same id for events inside that lifecycle, and mint a new id on lifecycle boundary.
- **Rationale:** Balances resource-oriented tracing with operational clarity for non-track actions that still need isolated troubleshooting context.
- **Alternatives considered:**
  - Per-request correlation ids only: rejected due to fragmented troubleshooting context.
  - Session-wide single correlation id: rejected due to oversized noisy traces.

### 14) Transport state becomes server-tracked again
- **Decision:** Track a canonical `playing` / `paused` / `stopped` transport state as part of playback context, updated only by an explicit client-reported transport command; `stopped` also resets the bookmarked position to zero.
- **Rationale:** Restoring "what the user was last doing" across a client reconnect requires the server to remember transport intent, not just playlist/track/mode/position; this reverses the earlier client-only transport decision (see Decision 1) now that persistence-across-reconnect is an explicit goal. `stopped` is kept as a distinct value (rather than folding it into `paused`) because reset-to-zero cannot be inferred passively from streaming activity.
- **Alternatives considered:**
  - Keep transport fully client-local (previous MVP decision): rejected because it cannot satisfy state restoration on reconnect and gives the server no signal to distinguish "resume-eligible" from "fresh" playback context.
  - Binary playing/paused only: rejected because it loses the one signal (explicit reset) that can't be recovered any other way.

### 15) Playback position combines an optional client report with a passive streaming-derived fallback
- **Decision:** Accept an optional, explicit client-reported position (track id + seconds) as authoritative when present and plausible. When absent, or when a report implies playback beyond what has actually been streamed for that track, fall back to a best-effort position derived from observed audio streaming activity for the current track only. No dedicated seek endpoint exists; seeking and natural progress are reported the same way.
- **Rationale:** A client is the only generic source of truth for "what is currently audible," independent of buffering/decode strategy, so explicit reporting must stay optional and authoritative. Passive observation of streamed byte ranges is a free, best-effort fallback and plausibility bound, but is not treated as canonical, since it only reflects data transfer, not rendering; restored-position accuracy is intentionally a nice-to-have, not a correctness guarantee.
- **Alternatives considered:**
  - Canonical range-inference only (no client report): rejected — assumes a particular streaming/buffering cadence (e.g. small-window progressive fetch) that a custom client with deep prefetching would violate, leaking a client-implementation assumption into the API.
  - Mandatory client-reported position only: rejected — an absent or unreliable reporter would leave position permanently unrestored, when a passive fallback costs nothing.

### 16) Playback REST commands narrow to entity-reference-only operations
- **Decision:** The playback command surface is: select playlist, select track, advance to next track (server-driven per mode), set mode, set transport state, and (optionally) report position. Dedicated `/play`, `/pause`, `/stop`, and `/seek*` endpoints are removed.
- **Rationale:** Matches the "client supplies entity references, server owns domain logic" principle; a smaller, uniform command set is easier to keep generic across client architectures.
- **Alternatives considered:**
  - Keep dedicated seek endpoints alongside position reporting: rejected as redundant — seeking is just a new position report, not a distinct domain operation.

### 17) API must remain agnostic to client playback architecture
- **Decision:** Declare, as a formal requirement in `player-rest-api`, that the API must not assume any particular client audio pipeline, buffering strategy, or streaming cadence, and validate design choices against a "swap in a fully custom playback engine" thought experiment.
- **Rationale:** This constraint directly caused Decision 15 to reject canonical range-inference; without stating it explicitly, a plausible-sounding but leaky mechanism could be reintroduced later.
- **Alternatives considered:**
  - Leave it as an implicit design value: rejected — it already caused one leak that had to be found and walked back during design.

### 18) Switching active playlist eagerly re-picks a current track
- **Decision:** When the active playlist changes, the server immediately selects a current track from the new playlist (consistent with the active mode) rather than leaving current-track resolution to a later lazy read.
- **Rationale:** Keeps the "client never performs playlist/selection logic" principle consistent for playlist switches, not just for next-track advancement; avoids a hidden cross-service side effect where a stale current-track id silently self-heals only when next queried.
- **Alternatives considered:**
  - Lazy normalize-on-read (previous MVP behavior): rejected as a surprising, hard-to-discover cross-service coupling.

### 19) Passive observed position is monotonic
- **Decision:** Keep the passive stream-derived position as the maximum observed position for the current track. Backward seeks are expressed through an explicit client position report; streaming observations never move the fallback backward.
- **Rationale:** Range requests may arrive out of order or represent prefetch/probe activity rather than audible playback. A monotonic maximum avoids regressing the fallback because of a late or unrelated lower range, while explicit reports remain the client-architecture-independent path for backward seeks. With this invariant, a plausible explicit report remains authoritative even when later streaming observes more bytes.
- **Alternatives considered:**
  - Most recent observed range: rejected for MVP because out-of-order and non-current playback fetches could make restored position regress unpredictably.

## Risks / Trade-offs

- **[Filesystem scan cost on large libraries]** -> Mitigate with deterministic startup scan, progress logging, and optional future incremental refresh endpoint.
- **[Missing or malformed metadata for duration]** -> Mitigate by graceful fallback (`unknown`) plus warning logs.
- **[Server playback model without actual audio output device]** -> Mitigate by defining playback state machine independent of host audio hardware.
- **[Clients omit CorrelationId and lose trace continuity]** -> Mitigate with server-side ID generation, warning logs, and client defaults that always send IDs.
- **[No persistence causes restart data loss]** -> Mitigate by documenting MVP behavior and ensuring rapid re-index on startup.
- **[Correlation boundary ambiguity for edge transitions]** -> Mitigate by explicitly documenting lifecycle start/end rules for track replacement, auto-next, manual seek, replay, stop, playlist switch, and playback mode change.
- **[Passive position fallback is approximate]** -> Mitigate by treating restored position as best-effort/nice-to-have, not a correctness guarantee, and by always preferring an explicit, plausible client-reported position when one exists.
- **[Streaming layer needs a narrow link into playback state for fallback observation]** -> Mitigate by keeping that link strictly read-fallback (never authoritative) and scoped to the current track only, per Decision 15.

## Migration Plan

1. Add/align backend domain services and REST contracts while preserving current package boundaries.
2. Implement catalog scan + default playlist initialization on startup.
3. Implement/complete playback context model and endpoints (mode, track selection, transport, next-track advance, optional position reporting).
4. Implement/complete range streaming endpoints and tests, keeping streaming side-effect free of authoritative playback state (Decisions 15, 17).
5. Add Docker image and Compose runtime docs for mounted `/music` usage (server-only validation this iteration; no client UI work).
6. Validate with integration and API tests, then publish server-scoped MVP docs for operations and agent workflow. Propose the browser client as a follow-up change once this validation is complete.

Verification policy: use clean-state verification commands (`mvn clean ...` and `docker compose down -v` when container state must be reset) before final validation.

Rollback strategy: redeploy prior image tag; because state is in-memory only, no data migration rollback is needed.

## Confirmed Decisions

- Missing `CorrelationId` is handled with server-generated fallback plus warning log.
- Random mode is pure random per step; shuffle-without-repeat is deferred.
- Unsupported playlist create/update/delete operations return `501 Not Implemented`.
- Track length metadata uses `jaudiotagger` with graceful unknown fallback.
- Transport state (`playing`/`paused`/`stopped`) is server-tracked and restorable across client reconnects; `stopped` explicitly resets position to zero.
- Playback position combines an optional explicit client report with a passive, best-effort fallback derived from observed streaming activity for the current track; restored-position accuracy is intentionally best-effort.
- Dedicated seek endpoints are removed; seeking is expressed the same way as any other position report.
- Switching the active playlist eagerly re-picks a current track instead of lazily nulling it out.
- The REST API must remain agnostic to any specific client audio pipeline or buffering strategy; this is now a declared requirement in `player-rest-api`.

## Correlation Scope Notes

- A lifecycle starts when a track is selected/prepared for playback.
- The same correlation id spans load, metadata availability, play/pause, seek, waiting/stalled, and ended events for that track.
- A lifecycle also starts when a playlist switch or playback mode change operation is initiated.
- A lifecycle ends when playback transitions to a different track, current track context is explicitly replaced, playlist is switched, or playback mode changes.
- Potential ambiguity to enforce in implementation/tests: replaying the same track after ended (new lifecycle), and stop-then-play-same-track behavior (treat as new lifecycle for clean troubleshooting boundaries).

