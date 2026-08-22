# Tasks: Track Catalog and Persistent Playlists

Component discipline for all groups: follow the component model in design.md (Component Model and Testability) — narrow single-responsibility components, injected dependencies (filesystem, clock, repositories, resolvers), pure policy logic separated from I/O.

## 1. Persistence foundation

- [x] 1.1 Add dependencies: `spring-boot-starter-data-jpa`, Hibernate community SQLite dialect, `org.xerial:sqlite-jdbc`, `org.flywaydb:flyway-core`; configure SQLite datasource (url from `gravifon.config-dir`, WAL mode, foreign keys on)
- [x] 1.2 Add `configDir` property to `GravifonProperties` (default `/config`), create directory on startup if absent
- [x] 1.3 Write Flyway migration `V1__init.sql`: `track`, `track_metadata`, `playlist`, `playlist_entry`, `playback_state` tables + `expires_after` partial index (per design D7)
- [x] 1.4 Integration test: migrations apply cleanly on empty DB and are repeatable across restarts

## 2. Track registry (renamed from media catalog)

- [x] 2.1 Rename `catalog` package → `registry`, `MediaCatalogService` → `TrackRegistry`; rework `Track` model: kind (FILE/STREAM), metadata map (multi-value, ordered), first-class duration, state (failing + structured error), file fields (relPath, format), stream fields (sourceUrl, streamUrl, expiresAfter)
- [x] 2.2 Implement identity derivation per design D2: `sha1("file:"+relPath)`, `sha1("stream:"+normalizedSourceUrl)`; `SourceUrlNormalizer` interface with conservative default; pure unit tests
- [x] 2.3 Implement `TrackRepository` (Spring Data JPA) with metadata child rows and cascades; adapter tests against real SQLite
- [x] 2.4 Extract `MetadataExtractor` interface + jaudiotagger adapter (full tag set: artist, album, title, album artist, date, track, track total, genre, custom keys, duration)
- [x] 2.5 Extract `LibraryScanner` behind a filesystem seam (Jimfs-friendly); implement `ScanReconciler` as pure merge policy per design D5: create / refresh metadata+state / tombstone referenced-missing (`SOURCE_MISSING`) / remove unreferenced-missing / resurrect returning files
- [x] 2.6 Unit-test the reconciliation matrix with Jimfs fixtures (new/changed/missing-referenced/missing-unreferenced/unreadable/recovered); unit-test state transitions per D4

## 3. Persistent playlists

- [x] 3.1 Rework `Playlist` model: ordered track references, per-playlist playback mode; implement `PlaylistRepository` with entry ordering
- [x] 3.2 Replace `InMemoryPlaylistService` with database-backed `PlaylistService` (list, get, select, create, rename, reorder, add/remove entries, delete); no implicit/default playlists anywhere
- [x] 3.3 Catalog materialization descriptor: create playlist from full registry snapshot; result is an ordinary playlist, not auto-updated
- [x] 3.4 Service tests: shared-track propagation (metadata edit visible in all playlists), ordering round-trip, unknown-track rejection, no-playlists-on-fresh-install

## 4. Stream source SPI and track creation

- [x] 4.1 Define `StreamResolver` SPI per design D13 (list-in/list-out: `resolveTracks` → 1..N prototypes, `refreshStream`); `StreamResolverRegistry` routing by `supports()`; zero concrete resolvers shipped
- [x] 4.2 Registry integration: derive IDs from resolver prototypes, dedupe, persist new stream tracks with supplied metadata and duration
- [x] 4.3 Tests: single-URL-single-track, single-URL-multi-track (album case), multi-URL expansion, dedupe on re-add, no-resolver failure path

## 5. Playback state persistence

- [x] 5.1 `PlaybackStateRepository` keyed by `session_id` (`default` row); persist active playlist, current track, transport, position + origin on every mutation
- [x] 5.2 Restore state at startup; normalize `PLAYING` → `PAUSED` (retain position)
- [x] 5.3 Normalize `PLAYING` → `PAUSED` on explicit client init request (`POST /api/playback/init`)
- [x] 5.4 Move playback mode resolution from global field to active playlist's stored mode; mode changes write through to the playlist row
- [x] 5.5 Integration test: restart Spring context against pre-seeded DB, assert paused-at-position restoration

## 6. Source-aware streaming

- [x] 6.1 Introduce track-kind seam in `AudioStreamingController`: FILE → existing file streaming path (unchanged), STREAM → proxy
- [x] 6.2 Implement stream proxy: forward Range headers, pass through partial content, handle upstreams without range support; hide `streamUrl` from responses
- [x] 6.3 Refresh-at-request fallback per design D11 fault-tolerance rules: bounded timeout, limited retries, single in-flight refresh per track, failed refresh never corrupts persisted state; failure → mark track `STREAM_UNREACHABLE` + 5xx
- [x] 6.4 Define `TagWriteBackHandler` SPI + no-op file implementation (accept and discard); wire into metadata update flow
- [x] 6.5 Preemptive refresh scheduler: configurable look-ahead/cadence/scope (`gravifon.streams.*`), active-playlist-plus-next scope, persist refreshed URL + expiry, clear `STREAM_EXPIRED` state
- [x] 6.6 Proxy tests with stubbed upstream (fresh/expired/refresh-failure/timeout/slow-refresh/concurrent-same-track/range-passthrough); scheduler tests with controllable clock

## 7. API surface

- [x] 7.1 Track details endpoint returns kind, full metadata map, duration, state, stream source info (never `streamUrl`); track summaries keep stable IDs
- [x] 7.2 `PATCH /api/tracks/{id}/metadata` — transactional metadata update + write-back handler invocation
- [x] 7.3 `POST /api/tracks/{id}/state` — client failure report / explicit clear; no auto-clear on successful streaming
- [x] 7.4 Playlist mutation endpoints: create (track ids / stream URLs / catalog descriptor), patch (rename/reorder/entries/mode), delete; validation errors on unknown track refs; multi-track expansion from a single source URL
- [x] 7.5 Remove existing reference SPA static assets (SPA will be rewritten from scratch); update response mappers + WebMvcTests for all new/changed endpoints

## 8. Implementation review checkpoint

- [x] 8.1 Review the implementation against the design.md component model: each component has a single narrow responsibility; policy logic (reconciliation, identity derivation, refresh scope, transport normalization) is free of I/O and unit-tested without Spring/DB/filesystem
- [x] 8.2 Verify seam injection: filesystem (Jimfs-tested), clock (controllable in tests), repositories (interface-bound), resolvers (registry-routed); flag any static/hidden dependency
- [x] 8.3 Verify edge-case test coverage enumerated in design.md (empty registry, missing files, expired URLs with slow resolvers, concurrent refresh of same track, malformed metadata, unknown-track references) — all present and green

## 9. Runtime, docs, and verification

- [x] 9.1 `docker-compose.yaml`: add operator-selected `GRAVIFON_LIBRARY_PATH` and `GRAVIFON_CONFIG_PATH` sources with Docker-managed fallback volumes; Dockerfile: declare `/music` and `/config`; document volume and application-property conventions in README
- [x] 9.2 Logging review per project severity conventions; correlation-id coverage for new endpoints
- [x] 9.3 `mvn clean test` and `mvn clean verify` (JaCoCo ≥80% gate)
- [x] 9.4 Container verification: `docker compose down -v && docker compose up --build`, smoke-test persistence across container recreation on the host
