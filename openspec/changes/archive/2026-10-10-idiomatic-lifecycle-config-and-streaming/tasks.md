# Tasks

## 1. Scoped Configuration Foundation

- [x] 1.1 Create `LibraryProperties` record (`gravifon.library.*`: `@NotNull musicRoot`, `startupScanEnabled`) and `StreamRefreshProperties` record (`gravifon.streams.refresh-*`: enabled flag, positive look-ahead/interval/timeout `Duration`s, `@Min(1)` max attempts, `RefreshScope` enum) with constructor binding, `@Validated`, and `@DefaultValue`; verify per-record tests cover defaults and rejection of invalid values
- [x] 1.2 Create `StreamProxyProperties` record (`gravifon.streams.proxy-*`: connect/read timeouts, defaults 5s/15s) and update `HttpStreamProxy` to consume it; verify binding through configuration tests
- [x] 1.3 Delete `GravifonProperties` (dropping the Java-unconsumed `config-dir`), switch `GravifonApplication` to `@ConfigurationPropertiesScan`, and update `application.yml` to the `gravifon.library.*` keys; verify `mvn clean test` passes and the datasource `${gravifon.config-dir}` placeholder still resolves
- [x] 1.4 Update consumers (`LibraryScanCoordinator`, `TrackRegistry`, `StreamRefreshService`, `StreamRefreshScheduler`) to inject their scoped record; the scheduler consumes the bound refresh-interval property instead of duplicating the `PT10M` placeholder default; verify `StreamRefreshSchedulerTest` passes
- [x] 1.5 Migrate `LibraryScanCoordinatorTest`, `TrackRegistryTest`, and `StreamRefreshServiceTest` fixtures to per-scope record construction (replacing `GravifonPropertiesTest`); verify `mvn test -Dtest='*Properties*,*ScanCoordinator*,*TrackRegistry*,*StreamRefresh*'` passes

## 2. Startup Lifecycle

- [x] 2.1 Enable virtual threads (`spring.threads.virtual.enabled=true`) and define the managed virtual-thread executor bean; verify the application context starts and `mvn clean test` passes
- [x] 2.2 Move `LibraryScanCoordinator.scanOnStartup` from `@PostConstruct` to an `@EventListener(ApplicationReadyEvent.class)` + `@Async` trigger that calls `scan()` through the bean proxy; add a startup-trigger test verifying the scan is invoked once when enabled and not when disabled
- [x] 2.3 Move `PlaybackService.restoreState` from `@PostConstruct` to an `ApplicationRunner` (running before `ApplicationReadyEvent`); verify `PlaybackRestartIntegrationTest` passes unchanged
- [x] 2.6 Add a diagnostic mount-probe `ApplicationRunner` logging the resolved library path and whether it is empty (info level, never fail-fast); verify the startup log output in an application-context test for populated, empty, and missing library paths
- [x] 2.4 Introduce last-scan outcome tracking (success/failure, completion time, error detail) recorded by the scan trigger for health reporting; verify unit coverage of the recorded outcomes, including failure
- [x] 2.5 Record the lifecycle conventions (no `@PostConstruct` work; runners/events for startup tasks) in `AGENTS.md`

## 3. Byte-Range Streaming via Spring Abstractions

- [x] 3.1 Replace `parseRange`/`ByteRange` with `HttpRange.parseRanges` + single-region extraction, rejecting multipart (size ≠ 1) with 416; replace parser unit tests with range-contract tests covering single, open-ended, suffix, unsatisfiable, malformed, and multipart ranges
- [x] 3.2 Emit RFC 7233-compliant 416 responses with `Content-Range: bytes */<length>`; verify via `AudioStreamingControllerWebMvcTest` assertions on status and header
- [x] 3.3 Replace the content-type switch with `MediaTypeFactory` falling back to `application/octet-stream`; verify existing mp3/flac/ogg content-type tests pass
- [x] 3.4 Preserve `playbackService.observeStream(...)` best-effort observation semantics; verify the observation test in the streaming test suite passes

## 4. RestClient-Based Stream Proxy

- [x] 4.1 Inject `RestClient.Builder`, build the client with `JdkClientHttpRequestFactory` configured from the new proxy-timeout properties, and remove the dual-constructor `@Autowired` hack; verify context wiring via a slice test
- [x] 4.2 Implement proxying through `exchange()` with no error-status handler so upstream 4xx/5xx statuses pass through, streaming the body unbuffered and preserving the pass-through header set; verify `HttpStreamProxyTest` rewritten on `MockRestServiceServer` covers range forwarding, full-response fallback, upstream 503 pass-through, and refresh-failure short-circuit

## 5. Expiry-Aware Stream Refresh, Decomposition and Managed Concurrency

- [x] 5.0 Introduce the freshness policy and reshape the refresh configuration: add `StreamTrack.needsRefresh(now, margin)` (missing URL or `expiresAfter <= now + margin`; present URL with null expiry is non-expiring) replacing `isStreamUrlFresh`; in `StreamRefreshProperties` replace `refreshScope`/`RefreshScope` with `@Min(1) refreshUpcomingCount` (default 10, counting the current track), add `refreshMinValidity` (default `PT1M`), and enforce `refreshAhead >= refreshInterval + refreshUpcomingCount × refreshTimeout × refreshMaxAttempts` and `minValidity < refreshAhead`; update `application.yml`, `ScopedPropertiesTest`, and `TrackPolymorphismTest` (reopens the `RefreshScope` code from 1.1); verify boundary tests for `needsRefresh` and property binding/rejection tests pass
- [x] 5.1 Extract a pure `RefreshPlanner.plan(upcoming tracks, now, ahead)` returning due `StreamTrack`s ordered by urgency (missing URL first, then earliest expiry), ignoring non-stream tracks; verify planner unit tests with zero mocks cover not-due, inside-window, exact boundary, expired, null URL, null expiry with a URL, non-stream tracks, and ordering
- [x] 5.2 Add the playback-side seam: `TrackSelector.upcoming(trackIds, currentTrackId, limit)` (sequential: current + wrap-around successors capped at playlist size without repeats; random: current only) and `PlaybackService.upcomingTracks(limit)` (selector for the session mode over the active playlist, one batch track load, empty when no active playlist or current track); verify selector unit tests (wrap-around, limit larger than the playlist, empty playlist, unknown current track, random) and a service test with scenario-level stubs
- [x] 5.3 Extract `StreamTrackRefresher.ensureValid(track, margin)` (retry × timeout, per-track single-flight re-checking with the caller's margin, resolver-result validation, failure policy) running on the managed executor, replacing `CompletableFuture.supplyAsync` on the common pool; `HttpStreamProxy` passes `minValidity`; verify tests for retry, timeout, dedup, no common-pool usage, rejected near-expired resolver results, failure marking only when the current URL is expired or missing, and serving the still-valid URL when a refresh fails
- [x] 5.4 Reduce `StreamRefreshService` to a thin, sequential, tracks-only orchestrator (`refreshDue(tracks, now)` = planner + refresher, one track's failure not blocking the next); move `StreamRefreshScheduler` to the `streaming` package as a bare trigger (`refreshDue(playbackService.upcomingTracks(count), now)`); verify `StreamRefreshServiceTest` and `StreamRefreshSchedulerTest` pass with scenario-level stubs, a regression test that a still-valid URL expiring inside the look-ahead window is actually refreshed, and that `registry.stream` has no imports from the `playlist` or `playback` packages
- [x] 5.5 Drop the timing invariant and the enabled flag (reopens 5.0 and the 1.1/1.4 refresh configuration): remove `refreshInterval` and `refreshEnabled` from `StreamRefreshProperties`, delete the `refreshAhead >= interval + …` and `minValidity < refreshAhead` validation (keeping positive-`Duration`, `@Min(1)` count and attempts checks), remove the `refreshEnabled` early return from `StreamRefreshService`, and add `refresh-cron` (`${GRAVIFON_STREAM_REFRESH_CRON:0 */10 * * * *}`) to `application.yml`; update `ScopedPropertiesTest`, `StreamRefreshServiceTest` (drop the disabled-refresh case) and fixtures that construct the record; verify `mvn test -Dtest='*Properties*,*StreamRefresh*,*RefreshPlanner*'` passes and that no test asserts startup rejection of timing combinations
- [x] 5.6 Switch `StreamRefreshScheduler` to `@Scheduled(cron = "${gravifon.streams.refresh-cron}")`, removing the bean-name SpEL `fixedDelayString` and `TimeUnit` import; the tick body stays a single delegating line and the class no longer needs the refresh record for scheduling (only `refreshUpcomingCount`); replace `GRAVIFON_STREAM_REFRESH_INTERVAL` / `GRAVIFON_STREAM_REFRESH_ENABLED` with `GRAVIFON_STREAM_REFRESH_CRON` in `README.md`, `docker-compose.yaml` and any other docs, noting `-` disables proactive refresh; verify `StreamRefreshSchedulerTest` still passes and `mvn clean verify` boots the application context with the default cron (framework cron parsing and `-` handling are trusted, not re-tested)

## 6. Structured Track-Failure Construction

- [x] 6.1 Introduce `TrackErrorReporter` enum and named `TrackError` factory methods; replace magic reporter strings and inline `new TrackError(...)` in `LibraryScanCoordinator`, `TrackRegistry`, and `JpaTrackRepository`; verify `TrackErrorTest`, scan coordinator, and persistence tests pass with reporter persisted as the enum name (no schema change)

## 7. Operational Health Surface

- [x] 7.1 Configure `management.*` in `application.yml`: web exposure for health, `endpoint.health.probes.enabled=true`, readiness group containing exactly the database and `musicLibrary` (never scan state), liveness with no custom contributors; verify `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` respond as configured in an integration test
- [x] 7.2 Implement the `musicLibrary` health indicator: UP when the music root exists and is readable (no writability check — the mount is legitimately read-only), DOWN with detail otherwise, with the filesystem call executed on the managed executor and bounded by a timeout (timeout ⇒ DOWN); verify unit tests for accessible, missing, and timeout paths, and that the health request thread never performs the filesystem call
- [x] 7.3 Implement the `libraryScan` health component reporting the last scan outcome from 2.4 (status UP with outcome detail; failures shown as detail without degrading aggregate health); verify health endpoint tests cover successful and failed scan reporting
- [x] 7.4 Add a `healthcheck` to `docker-compose.yaml` polling the health endpoint (choosing a probe mechanism available in the runtime image) and document health/liveness/readiness endpoints in `README.md`; verify with `docker compose config` and `docker compose up --build`

## 8. Verification

- [x] 8.1 Run `mvn spotless:apply`, `mvn clean test`, and `mvn clean verify`; verify the JaCoCo coverage gate passes
- [x] 8.2 Run `docker compose down -v && docker compose up --build`; verify the container reports healthy and the player serves the persisted registry while the initial scan runs
- [x] 8.3 Run `openspec validate idiomatic-lifecycle-config-and-streaming --no-interactive` and record the final conventions state in `AGENTS.md`
