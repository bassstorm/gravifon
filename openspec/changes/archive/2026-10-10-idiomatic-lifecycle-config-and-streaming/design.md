# Design

## Context

See proposal.md - Why. The change touches four infrastructure seams of the service: startup lifecycle (`LibraryScanCoordinator`, `PlaybackService`), configuration binding (`GravifonProperties` and consumers), HTTP streaming (`AudioStreamingService`, `HttpStreamProxy`), and operational health (Actuator, Compose). No backward compatibility or migration constraints exist; the cleanest idiomatic design wins in each seam. The codebase is small (~3.6k lines of main code), so cross-cutting rework is cheap if kept cohesive.

## Goals / Non-Goals

**Goals:**
- Startup does no blocking work in bean initialization; all startup work runs through Spring Boot lifecycle mechanisms (`ApplicationRunner`, `ApplicationReadyEvent`) on managed threads.
- Configuration is immutable, constructor-bound, validated at startup, with defaults declared exactly once.
- HTTP semantics (byte ranges, content types, upstream status handling) come from Spring abstractions, not hand-rolled parsers.
- Stream refresh is expiry-aware (proactive look-ahead and request-time minimum validity share one freshness policy), tracks-only (no playlist/playback coupling), and runs on a managed executor with pure, mock-free-testable planning logic.
- Health, liveness, and readiness are first-class operational endpoints consumed by Compose.

**Non-Goals:**
- `PlaylistService` public-API pruning and its in-memory active-playlist pointer, and the `PlaybackService` session/persistence duality — see the playlist/playback redesign non-goal below.
- Multipart (`multipart/byteranges`) range responses — explicitly rejected by contract (see `audio-streaming` delta).
- Changing any operator-facing property key, environment variable, or API contract beyond those listed as breaking in the proposal (library keys, `refresh-scope` → `refresh-upcoming-count`, `refresh-interval`/`refresh-enabled` → `refresh-cron`, new `refresh-min-validity`).
- A playlist/playback model redesign, deferred to a dedicated follow-up change that this change deliberately leaves room for: entry-position identity (playlists may contain duplicate entries, which track-id-based "current track" lookups cannot distinguish), a single source of truth for the active playlist and playback mode, `PlaylistService` API pruning, the session/persistence duality, persisted decided-ahead random picks, and session reconciliation when playlist edits remove or reorder the current entry. Only the `restoreState` lifecycle move and the narrow `PlaybackService.upcomingTracks(limit)` seam (D6) happen here.
- Concurrent refresh of multiple tracks per tick — the orchestrator is deliberately sequential until the first real resolver informs the concurrency design.
- Introducing a web-framework-level resource handler (e.g. `ResourceHttpRequestHandler`) for audio — the controller/service split stays.

## Decisions

### D1: Startup scan on `ApplicationReadyEvent` + virtual threads; state restore on ordered `ApplicationRunner`

- `spring.threads.virtual.enabled=true` (Boot 3.5 first-class support) makes `@Async`/`SimpleAsyncTaskExecutor` use virtual threads — the right home for jaudiotagger-heavy scan I/O, which blocks on filesystem/network-mount reads.
- The scan trigger becomes an `@EventListener(ApplicationReadyEvent.class)` + `@Async` component calling `LibraryScanCoordinator.scan()` **through the bean proxy**, which also fixes today's `@Transactional` self-invocation bypass (the `@PostConstruct` path called `this.scan()` directly, silently dropping the declared transaction).
- Ordering inside startup: `ApplicationRunner` beans run before `ApplicationReadyEvent` fires, so `PlaybackService.restoreState` moves to an `ApplicationRunner` and completes deterministically before the scan starts. Restore is a single indexed read — it stays synchronous.
- Alternative considered: `ApplicationRunner` for the scan too — rejected because it delays the ready state by the full scan duration on large/slow libraries, contradicting the lightweight-startup goal. The accepted trade-off (transient 404s for not-yet-reconciled tracks) is codified in the `track-registry` delta.
- Alternative considered: `@PostConstruct` retained but made non-blocking — rejected; bean-initialization callbacks are the wrong seam for work that needs proxies, executors, and failure isolation.

### D2a: Mount probe — startup diagnostic, continuous readiness contributor

- The mount check is separated from the scan: probing "is `/music` present and readable *right now*" is an instantaneous syscall with nothing in common operationally with a full library reconciliation.
- **Startup**: an `ApplicationRunner` performs a fast mount probe and logs the resolved library path and whether it is empty (info level) — purely diagnostic, never fail-fast. Rationale: inside a container, the failure modes a startup probe could gate on barely exist — Docker auto-creates missing bind-mount sources and the supported fallback-volume scenario presents identically (existing, readable, *empty* directory) to the dangerous ones (typo'd host path, forgotten mount, NAS boot race). A startup probe cannot distinguish "empty library" from "wrong mount", so it must not gate. The visible "scanned 0 files" signal covers operator awareness.
- **Runtime**: a `musicLibrary` health indicator continuously evaluates mount presence/readability and is a member of the readiness group. This covers the one scenario startup cannot: the mount breaking *after* startup (NAS reboot, network drop on a LAN mount). DOWN is honest there — file streaming is genuinely impaired — and recovery is automatic when the mount returns.
- The check must never execute on the health-endpoint request thread: a stale NFS handle can hang a syscall for minutes. The indicator submits the filesystem call to the managed virtual-thread executor and bounds it with a short timeout; timeout ⇒ DOWN. Cost of a hung mount is one parked virtual thread, not a frozen health endpoint.
- Read-only is legitimate (`/music:ro` in the committed Compose file): the probe checks existence + readability only, never writability.
- `/config` gets no dedicated probe: the auto-configured database health indicator already covers H2 reachability, which is the only way the config dir materially fails.

### D2b: Scan outcomes are observable but never gate readiness

- The library scan plays no role in readiness or liveness. Rationale: scanning is a routine reconciliation of the registry against the filesystem — triggered at startup today, potentially scheduled or manual tomorrow — and all triggers are semantically equal. A failed scan leaves a functional service serving the last known registry snapshot, which is exactly what "ready" means.
- Scan outcomes are surfaced where failures belong in this system: per-track failure states written by the reconciler, an error-level log entry, and a dedicated `libraryScan` health component reporting the last scan's outcome (completion time, success or failure with error detail) while keeping status UP — a failed routine scan is not service dysfunction and must not flip aggregate health or the Compose health check.

### D3: Scoped configuration records instead of an uber-properties class

The single `GravifonProperties` JavaBean is split into cohesive, constructor-bound records — one per consuming component. The consumer graph justifies the split: no class consumes more than one cluster (`LibraryScanCoordinator`/`TrackRegistry` → library, `StreamRefreshService`/`StreamRefreshScheduler` → refresh, `HttpStreamProxy` → proxy), so per-scope fixtures make tests construct exactly the configuration their unit needs and nothing more.

- `LibraryProperties` (`gravifon.library.*`): `musicRoot` (`@NotNull`), `startupScanEnabled`. **Key rename**: `gravifon.music-root` → `gravifon.library.music-root` and `gravifon.startup-scan-enabled` → `gravifon.library.startup-scan-enabled`; the `GRAVIFON_LIBRARY_DIR` / `GRAVIFON_STARTUP_SCAN_ENABLED` environment variables keep working via `application.yml` placeholders (no backward-compatibility constraint, and the new namespace is the honest one).
- `StreamRefreshProperties` (`gravifon.streams.refresh-*`): look-ahead, upcoming-track count, timeout, max attempts, minimum validity. The former `refreshScope` string is replaced (see D6) by `refreshUpcomingCount` (`@Min(1)`, default `10`: the current track plus nine successors) — the number of upcoming tracks the refresh covers — rather than a `RefreshScope` enum, which would encode playlist/playback knowledge into the refresh module. **Key change**: `gravifon.streams.refresh-scope` / `GRAVIFON_STREAM_REFRESH_SCOPE` → `gravifon.streams.refresh-upcoming-count` / `GRAVIFON_STREAM_REFRESH_UPCOMING_COUNT`; `refresh-min-validity` / `GRAVIFON_STREAM_REFRESH_MIN_VALIDITY` (default `PT1M`) is new. The refresh *schedule* is deliberately not part of the record: it is a cron expression, `gravifon.streams.refresh-cron` (`GRAVIFON_STREAM_REFRESH_CRON`, default `0 */10 * * * *`), consumed directly by `@Scheduled` through a property placeholder, with its default owned by `application.yml`. The value `-` disables the schedule (`Scheduled.CRON_DISABLED`), replacing the former `refresh-enabled` flag; **key change**: `refresh-interval` and `refresh-enabled` are removed. Cron syntax errors and missing values already fail startup in Spring, so no bespoke validation is written. No cross-field constraint ties the look-ahead, schedule, timeout, attempts and minimum validity together (see D6).
- `StreamProxyProperties` (`gravifon.streams.proxy-*`): connect/read timeouts (defaults 5s/15s, matching today's hardcoded values), consumed by `HttpStreamProxy`.
- `config-dir` is **dropped from Java entirely**: no code reads it — the only consumer is the `application.yml` datasource placeholder `${gravifon.config-dir}`, which resolves from the Environment without a properties class. H2 already fails fast with a clear error if the directory is bad, so a Java class existing solely for validation would be a smell.
- All records use `@DefaultValue` so `application.yml` placeholders keep working and each default is owned exactly once (the refresh cron is the one exception, owned by `application.yml` and consumed by `@Scheduled`; this eliminates the SpEL bean-name lookup that `StreamRefreshScheduler`'s `fixedDelayString` needed). `@Validated` per record: `@NotNull` paths, positive `Duration`s, `@Min(1)` max attempts — malformed `GRAVIFON_*` values fail fast at startup with a clear binding error.
- Registration moves to `@ConfigurationPropertiesScan` on `GravifonApplication`, replacing the single `@EnableConfigurationProperties`.
- Tests construct fixtures through the per-scope record constructors; `GravifonPropertiesTest` is replaced by per-record tests covering defaults and validation.
- Alternative considered: one root `GravifonProperties` record with nested records — rejected; it re-creates the uber-class shape, forces every consumer's tests to build irrelevant scopes, and adds a facade with no behavior of its own.

### D4: `HttpRange`/`ResourceRegion` in `AudioStreamingService`

- Parse with `HttpRange.parseRanges(header)`; a parsed list with size ≠ 1 → `416` (multipart out of contract). Convert the single range via `range.toResourceRegion(resource)` (or equivalent `FileChannel` math) and stream the region with the existing zero-copy `FileChannel.transferTo`.
- `416` responses are built with `Content-Range: bytes */<length>` per RFC 7233 — the header Spring's machinery produces and the current hand-rolled code omits.
- Content type resolution moves to `MediaTypeFactory.getMediaType(...)`, falling back to `application/octet-stream`.
- `playbackService.observeStream(...)` best-effort observation after transfer completion is preserved unchanged (spec-pinned).
- Alternative considered: delegating the whole endpoint to `ResourceHttpRequestHandler` — rejected; it would entangle track-registry lookup, stream-proxy dispatch, and playback observation into handler configuration for no behavioral gain.

### D5: `RestClient` with explicit status pass-through in `HttpStreamProxy`

- Inject `RestClient.Builder` and build a client with `JdkClientHttpRequestFactory` configured from the new proxy-timeout properties; this keeps the JDK transport while gaining Spring's request/response idiom and testability.
- Use `exchange()` (or an explicit no-op status handler) — **never** the default error handler: the `audio-streaming` spec requires upstream 4xx/5xx statuses to pass through to the client, which `DefaultResponseErrorHandler` would convert to exceptions. The existing `passesThroughUpstreamFailureStatus...` test guards this.
- The response body is streamed (not buffered) from the exchange result into the servlet output stream, preserving today's pass-through headers (`Content-Type`, `Content-Length`, `Content-Range`, `Accept-Ranges`).
- Tests switch from mocking `java.net.http.HttpClient` to `MockRestServiceServer`, the idiomatic RestClient test boundary; the dual-constructor `@Autowired` hack disappears.

### D6: Expiry-aware stream refresh — tracks-only decomposition + managed executor

**Problem.** Today the scheduler selects tracks whose `expiresAfter` falls inside the look-ahead window but then calls `ensureFresh(track, now)`, which returns early whenever the URL is still valid *at `now`*. The look-ahead window therefore never triggers a refresh: only already-expired or missing URLs are refreshed. The request path likewise serves a URL with zero remaining validity. Expiry must be a first-class input of both the planning and the refreshing logic, evaluated through one shared policy.

```
Before:                                    After:
StreamRefreshService (178 lines)           streaming.StreamRefreshScheduler -> bare @Scheduled trigger
 + playlist/playback scope computation       playback.PlaybackService.upcomingTracks(limit)
 + expiry threshold check (inert)               -> List<Track>; selector-driven, mode-aware
 + retry/timeout loop                      registry.stream (tracks only, no playback/playlist imports)
 + in-flight dedup map                       RefreshPlanner         -> pure: (tracks, now, ahead)
 + failure marking                               -> due StreamTracks ordered by urgency
 + supplyAsync(commonPool)                   StreamTrackRefresher   -> ensureValid(track, margin):
                                                 retry x timeout, single-flight, failure policy,
                                                 managed virtual-thread executor
                                             StreamRefreshService   -> thin sequential orchestrator
                                                 over planner + refresher; knows tracks only
```

**Freshness policy (domain).** `StreamTrack.needsRefresh(Instant now, Duration margin)` replaces `isStreamUrlFresh(now)`: true when `streamUrl == null`, or when `expiresAfter != null && !expiresAfter.isAfter(now.plus(margin))`. A present URL with `expiresAfter == null` is non-expiring (documented resolver contract). Two margins drive it:
- `refreshAhead` (existing): proactive lead time used by the scheduler/planner.
- `minValidity` (new, `gravifon.streams.refresh-min-validity`, default `PT1M`): the remaining validity a URL must have to be served, used by the request path (`HttpStreamProxy`).

**Look-ahead and schedule are independent concepts.** `refreshAhead` is a property of the data (how close to expiry a track counts as due); the cron schedule is a property of the trigger (when to look). They are intentionally not coupled by a startup invariant: playlists are dynamic, so the upcoming set changes independently of any sweep cadence anyway, and the proactive refresh is best-effort. If a schedule gap exceeds the look-ahead, or a URL expires before a sweep reaches it, the request path still guarantees correctness through `minValidity` and single-flight; the only cost is resolver latency at request time, which is normal for network streaming. Consequently there is no timing validation and no `minValidity < refreshAhead` check; implausible values degrade latency, not correctness.

**Tracks-only refresh module, playback-driven trigger.** `StreamRefreshService`, the planner and the refresher never see playlists or playback state, and `registry.stream` has no imports from `playlist` or `playback`. "Which tracks are upcoming" is answered by playback, which already owns the current track, the mode, and next-track selection; no indirection port is introduced (a port would only paper over a package cycle that moving the trigger removes):
- `StreamRefreshScheduler` moves from `registry.stream` to `streaming`, which already depends on `PlaybackService` (via `AudioStreamingService`) and on the refresh service (via `HttpStreamProxy`). It is a `@Scheduled(cron = "${gravifon.streams.refresh-cron}")` bare trigger (no run at startup; `-` disables it) whose tick is a single line: `refreshService.refreshDue(playbackService.upcomingTracks(properties.refreshUpcomingCount()), Instant.now())`; no decision logic lives in the `@Scheduled` method.
- `TrackSelector` gains `List<String> upcoming(List<String> trackIds, String currentTrackId, int limit)`: `SequentialTrackSelector` returns the current track followed by wrap-around successors (capped at the playlist size, no repeats); `RandomTrackSelector` returns only the current track, because the next random pick is not decided ahead of time.
- `PlaybackService.upcomingTracks(int limit): List<Track>` resolves the selector for the session mode over the active playlist and loads the tracks in one `findAllById` batch; with no active playlist or current track it returns an empty list.
- This is the stable seam. The planned playlist/playback redesign (see Non-Goals) may change what `upcoming` is computed from — entry positions instead of track ids, persisted random picks — without touching the refresh module. Known limitation until then: `upcoming` inherits the `indexOf`-by-track-id semantics of `selectNextTrack`, so duplicate playlist entries resolve to their first occurrence.
- `RefreshScope` is replaced by `StreamRefreshProperties.refreshUpcomingCount` (`gravifon.streams.refresh-upcoming-count`, `@Min(1)`, default `10`, counting the current track).

**Planner (pure, zero mocks).** `RefreshPlanner.plan(Collection<Track> upcoming, Instant now, Duration ahead): List<StreamTrack>` ignores non-stream tracks, keeps those with `needsRefresh(now, ahead)`, and orders by urgency (missing URL first, then earliest expiry). Unit tests cover the expiry boundaries (not due, inside window, exactly at the boundary, expired, null URL, null expiry with a URL) and ordering, in the style of `ScanReconciler`.

**Orchestrator.** `StreamRefreshService.refreshDue(Collection<Track>, Instant)` plans and then refreshes the due tracks one after another, a failure on one not preventing the next. Sequential on purpose: concurrency tuning waits for the first real resolver.

**Refresher.** `StreamTrackRefresher.ensureValid(StreamTrack, Duration margin)`:
- Single-flight per track id; the in-flight re-check re-reads the track from `TrackRegistry` and evaluates `needsRefresh(now, margin)` with the **caller's margin** (scheduler: `refreshAhead`; request path: `minValidity`).
- Bounded attempts with per-attempt timeout, run on the Spring-managed virtual-thread `Executor` bean (aligned with D1), replacing `ForkJoinPool.commonPool`.
- A resolver result is rejected as a failure when its `expiresAfter` is not after `now + minValidity`. A result still inside `refreshAhead` is accepted with a `warn` log; the schedule caps the refresh rate at once per track per run, so no hot loop forms.
- **Failure policy depends on the current URL.** If the persisted URL is still valid at `now` (or non-expiring), a failed refresh is logged at `warn`, the track is *not* marked failing, the request path serves the existing URL, and the scheduler retries next tick. If the URL is expired or missing, the track is marked failing (`STREAM_UNREACHABLE` / `STREAM_REFRESH_TIMEOUT`) and the failure propagates, as today. A successful refresh clears the failure state (`withStream` already resets it).

**Alternatives considered:**
- Keeping the class whole and only swapping the executor — rejected; the scope-planning tangle is the primary testability debt and it shares the file with the executor change.
- A planner returning track ids only — rejected; it cannot express expiry and forces the filter back into the orchestrator.
- Scope logic inside the scheduler — rejected; it would bury untestable decision logic in a trigger adapter. The decision now lives in playback (`upcomingTracks`), where mode and current-track knowledge already are.
- An `UpcomingTracks` port owned by the refresh module — rejected; it only avoids a `registry`↔`playback` package cycle, which moving the scheduler to `streaming` avoids without an extra abstraction.
- `Playlist.nextTracks(limit)` — rejected; the playlist knows neither the current position nor the effective mode strategy, so it would duplicate selector logic. Position-aware redesign is a planned follow-up.
- Expiry-driven one-shot scheduling per track — deferred; more precise but stateful, and a best-effort periodic sweep backed by the request-path fallback is sufficient.
- A startup timing invariant (look-ahead ≥ interval + worst-case cycle) or a look-ahead window derived from the schedule — rejected; it protects only a latency optimisation, couples two independent concerns, and cannot be evaluated for cron expressions without sampling them.
- A fixed-delay or fixed-rate `Duration` interval, or a programmatic `SchedulingConfigurer` — rejected in favour of cron: cron is a trusted framework path that fails fast on bad values, is disabled with `-`, and, unlike fixed-delay, is dispatched off the single scheduler thread when virtual threads are in use.
- Persisting an issue timestamp to derive a proportional lead time — rejected for now; needs a schema change with no demonstrated need.

### D7: `TrackErrorReporter` enum + `TrackError` factories

The reporter strings (`"SCAN"`, `"STREAM_REFRESH"`, `"CLIENT"`) become a `TrackErrorReporter` enum, and construction moves to named factories (e.g. `TrackError.scanFailure(...)`), removing duplicated `new TrackError(...)` blocks from `LibraryScanCoordinator`, `TrackRegistry`, and `JpaTrackRepository`. Persistence keeps storing the reporter as a string column (enum name) — no schema change, Flyway untouched.

### D8: Actuator health surface

- Explicit `management.*` config in `application.yml`: web exposure limited to `health` (and `info` if desired), `endpoint.health.probes.enabled=true`. Probe scoping is deliberate:
  - **Readiness** = database + `musicLibrary` mount indicator (D2a). Both are genuine serving impairments that can recover in place.
  - **Liveness** = Spring's default liveness state only, with no custom contributors. Every component failure mode this service has (dead mount, failed scan, unreachable database) is recoverable in place — a container restart fixes none of them, so a liveness-driven restart would be pure churn (in Compose, liveness has no consumer at all; the endpoint exists because probe-group config makes it free and future orchestrator users expect the URL).
  - The `libraryScan` outcome component (D2b) and `musicLibrary` also appear in the aggregate health endpoint.
- New `musicLibrary` health indicator (D2a): UP when `musicRoot` exists and is readable (read-only mount is legitimate — no writability check), DOWN with detail otherwise or on timeout; the filesystem call runs on the managed executor, never on the health request thread. The database indicator stays auto-configured; a test pins its presence.
- `docker-compose.yaml` gains a `healthcheck` (curl/wget against the health endpoint with interval/timeout/retries/start-period); README documents the three endpoints. Note: the runtime image is JRE-based and may lack curl — the healthcheck uses a JVM-based probe (e.g. `java` one-liner) or the image gains a minimal HTTP client; resolved during implementation (see Open Questions).

## Risks / Trade-offs

- [Async scan serves a stale/empty registry briefly] → Accepted and spec'd (`track-registry` delta); clients tolerate transient 404s, and scan progress/outcome is observable via the `libraryScan` health detail (D2b).
- [Hung LAN mount freezes the health endpoint] → The mount check runs on the managed virtual-thread executor with a bounded timeout (D2a); a stale NFS handle parks one virtual thread instead of the request thread.
- [Mount indicator flapping on network blips] → A single failing probe marks readiness DOWN, but Compose treats container health as informational (no restart without opt-in tooling) and the indicator recovers on the next successful evaluation; operators alert on the status rather than experiencing restarts.
- [Virtual threads + synchronized blocks in `scan()`/`PlaybackService` can pin carrier threads] → Pinning is short-lived and correctness-safe; the synchronized regions are intentional single-writer guards. Revisit only if JFR shows pinning hotspots.
- [`MockRestServiceServer` buffers or constrains streaming assertions] → Keep one low-level test asserting unbuffered streaming behavior through `exchange()` if the mock server proves limiting.
- [Stricter `416` responses could confuse a client that parsed today's headerless 416] → No such clients exist (no backward-compat constraint); the change makes responses more standards-compliant, not less.
- [`RANDOM` mode has no knowable next track] → `RandomTrackSelector.upcoming` returns only the current track (today's effective behavior); proactive prefetch of random successors needs playback to decide picks ahead of time, which is deferred to the playlist/playback redesign (see Non-Goals).
- [A failed scan is invisible to container-level health status] → Accepted by design: scan failure is routine and recoverable, so observability comes from the error log and the `libraryScan` health detail rather than container health; operators alert on logs, not restarts.

## Migration Plan

No data or operator migration. Deploy by rebuilding the image; the Compose health check takes effect on next `docker compose up --build`. Rollback is reverting the change — no persisted state formats change.

## Open Questions

- Health-check probe mechanism inside the JRE-only runtime image (curl availability vs. JVM-based probe) — an implementation detail that does not affect specs, approach, or task breakdown.
