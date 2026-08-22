# Design: Track Catalog and Persistent Playlists

## Context

Gravifon server today keeps all domain state in memory: the catalog is a list of
`Track(id, sourcePath, filename, format, durationSeconds)` records rebuilt from a
filesystem scan at startup, and playlists are `Playlist(id, name, trackIds)` maps
rebuilt alongside it. Nothing survives a restart, clients cannot mutate playlists
or metadata, and the model has no room for stream-backed tracks. The desktop app
being replaced demonstrates a proven shape (self-describing virtual tracks with
multi-value tags, per-track state, and expiring stream URLs) that we now port to
the server — with improvements identified during exploration (unified track
registry, shared track identity instead of per-playlist copies, derived identity
for all track kinds, server-owned preemptive stream refresh, database
persistence).

This change is cross-cutting (registry, playlist, playback, streaming, API,
runtime), introduces a new persistence stack (SQLite + Flyway + Spring Data
JDBC), and redefines the domain model — hence a design doc.

## Goals / Non-Goals

**Goals**
- One unified, persistent, metadata-bearing track registry (files and streams).
- Playlists as durable, mutable, ordered references to shared tracks. No
  special/default playlists.
- Playback context persisted across restarts, session-keyed for future growth.
- Stream-source plumbing that hides integration details from clients.
- Clients operate on a stable, fully-described model — no client-side tag parsing.

**Non-Goals** (explicitly out of scope)
- Any concrete stream integration (Bandcamp is a later capability on the SPI).
- Real tag write-back to audio files (no-op handler only).
- Metadata search/query endpoints (schema enables, API deferred).
- Multi-session/multi-room playback (schema admits, API deferred).
- Playlist import/export formats (m3u etc.).
- The browser SPA. It will be rewritten from scratch against the new API; any
  existing static assets are removed in this change so implementors are not
  confused by stale consumption patterns.

## Naming and Structural License

The MVP phase has no legacy consumers, so this change explicitly permits — and
where listed, mandates — renaming components to match the new architecture
rather than preserving outdated names:

- `catalog` package / `MediaCatalogService` → `registry` package /
  `TrackRegistry` (the component is the system of record, not a scan index).
- Spec capability `media-library-catalog` → `track-registry`.
- `InMemoryPlaylistService` → `PlaylistService` (database-backed).
- Existing reference SPA static assets are removed (see Non-Goals).

Further renames are allowed when a name encodes a concept this change removes;
they should be called out in the implementation summary.

## Key Decisions

### D1. Track registry is the single metadata-bearing entity

The catalog stops being a scan byproduct and becomes the **track registry**.
Every track — file or stream — exists exactly once, carrying metadata, duration,
and state. Playlists hold ordered references by track ID; they never copy
metadata or state.

Rationale: per-playlist copies (the desktop approach) create stale-copy hazards
with no reliable way to find "the other copies" at edit time. Shared identity
makes metadata edits and failure states naturally consistent everywhere. It also
matches the client goal: a playlist response is a list of IDs; full track
documents come from the track resource.

Consequence: browse-the-library views and playlist views read the same tracks.
`GET /api/tracks` is the library browse surface; no playlist semantics are
needed for browsing (see D6).

### D2. Track identity is derived for all kinds

No random IDs anywhere:

- **File track**: `sha1("file:" + relativePath)` — path relative to the music
  root. Stable across restarts *and* across container remounts at different host
  paths (the current absolute-path storage breaks under remount).
- **Stream track**: `sha1("stream:" + normalizedSourceUrl)` where normalization
  lowercases scheme/host and strips volatile query parameters. What counts as
  volatile is integration knowledge, so normalization lives behind a
  `SourceUrlNormalizer` interface with a conservative default (drops common
  tracking parameters, keeps the rest); resolvers may supply stricter
  normalizers.

Consequences: dedupe is structural (same source → same ID → primary key), not a
service-layer afterthought; reference counting for tombstone decisions (D5) is a
plain FK lookup. Accepted edge case: an upstream site redesign that changes URL
shape produces a new identity; the orphaned track is eventually removed by the
unreferenced-track rule (D5).

### D3. Metadata model: multi-value, ordered, extensible keys

Metadata lives in a `track_metadata` child table: `(track_id, key, value, ord)`.
Well-known keys (`ARTIST`, `ALBUM`, `TITLE`, `ALBUM_ARTIST`, `DATE`, `TRACK`,
`TRACK_TOTAL`, `GENRE`, ...) are a vocabulary, not a constraint — integrations
and future plugins may attach custom keys. Naming deliberately avoids "tag":
these values do not necessarily originate from file tags (integrations supply
them, users correct them).

`DURATION` is deliberately *not* metadata — it is a first-class typed column on
every track, populated for file tracks at scan time and for stream tracks at
creation/refresh time (streams routinely lack reliable duration and playback
math needs a typed value). Clients consume one uniform track document regardless
of kind.

File tracks get metadata snapshotted at scan time. Scan is the only component
that reads audio tags; clients and integrations never do.

### D4. State model: structured error, per-track, server-and-client writable

```
failing: boolean
last_error: { kind, message, at, reported_by }?
  kind        ∈ READ_ERROR | STREAM_UNREACHABLE | STREAM_EXPIRED | SOURCE_MISSING | ...
  reported_by ∈ SCAN | STREAM_REFRESH | CLIENT_REPORT
```

Rules (hammered out in exploration):
- Server sets state at scan time (unreadable file → `READ_ERROR`) and stream
  refresh time (resolution failure → `STREAM_UNREACHABLE`).
- Clients report playback failures via the track state endpoint.
- The server **never auto-clears** `failing` on playback success — it cannot
  observe success reliably. Clearing happens explicitly (client request, or a
  successful rescan/refresh replacing the error).
- Auto-skip of failing tracks is **client behavior**, not server logic. The
  server exposes state; clients decide skip-or-try.

`SOURCE_MISSING` participates in this model but is governed by the reference
rule in D5 (tombstone vs. removal).

### D5. Rescan reconciliation: tombstone only referenced tracks

Startup/rescan is a merge, not a rebuild. There is no default playlist, so the
registry itself is the file inventory:

- File present, track unknown → create track, extract metadata + duration.
- File present, track known → refresh metadata from disk, update state
  (newly unreadable → `READ_ERROR`; previously failing and now readable → clear).
- File missing, track known → **reference check**:
  - Referenced by ≥1 playlist → **tombstone**: keep the track, set
    `SOURCE_MISSING`. The user's playlist shows the gap; "this file vanished"
    is visible exactly where the user curated it.
  - Unreferenced → **remove** the track (and its metadata rows). The registry
    reflects what is on disk; there is no reason to keep debris nobody
    references.
- Missing file returns → the same relative path derives the same ID; the track
  resurrects in place if tombstoned, or is recreated as new if it was removed.

Stream tracks are never touched by scan reconciliation (no file to check); they
are removed only by explicit delete when their last playlist reference is
removed, or by a future housekeeping capability.

### D6. No default "All Tracks" playlist — on-demand materialization

The default-playlist concept is dropped entirely:

- No playlist is force-created at startup or scan time. The playlist store
  contains only user-created playlists.
- Catalog browsing is served by the track listing endpoint (`GET /api/tracks`),
  which always reflects the full registry.
- A client may materialize the whole catalog as a playlist:
  `POST /api/playlists` with a source descriptor (initially `{"from":
  "catalog"}`; filter-based descriptors are a future extension). The server
  snapshots current registry order into a normal, persisted playlist. From then
  on it is ordinary: reorderable, deletable, mode-configurable, and *not*
  auto-updated when the registry changes.

This removes magic IDs and special cases from the playlist service, and it makes
tombstone scoping (D5) natural: with no default playlist, unreferenced missing
files are simply removed.

### D7. Persistence: SQLite + Flyway + Spring Data JPA

Sized against the target library (~50k tracks): JSON-per-playlist storage was
the initial instinct, but routine metadata edits (integration cleanup workflows
edit tracks in batches) would each rewrite a ~25 MB blob. SQLite gives O(1)
edits, indexed expiration queries, and transactional playlist mutation at the
cost of one embedded dependency. JSON remains the API wire format.

- DB file: `{configDir}/gravifon.db`, `configDir` defaulting to `/config`
  (new `gravifon.config-dir` property alongside `music-root`). The image declares
  `/music` and `/config` as volumes. Compose maps Docker-managed fallback volumes
  by default and accepts `GRAVIFON_LIBRARY_PATH` and `GRAVIFON_CONFIG_PATH` for
  operator-selected host directories or named volumes. Application paths remain
  configurable through `GRAVIFON_LIBRARY_DIR` and `GRAVIFON_CONFIG_DIR`.
- Flyway owns schema evolution (`V1__init.sql`, ...). Migrations run at boot
  before the startup scan touches the database.
- Spring Data JPA (chosen during implementation over Spring Data JDBC) with
  explicit `@Entity` mappings in a dedicated `persistence` package; domain
  records stay persistence-free and repositories translate at the boundary.
  Entities use Lombok for boilerplate; domain records stay plain per project
  conventions. Repositories sit behind interfaces so domain services remain
  unit-testable without a database.

Schema (V1):

```sql
track
  id             TEXT PRIMARY KEY     -- sha1("file:"+relPath) | sha1("stream:"+normSourceUrl)
  kind           TEXT NOT NULL        -- FILE | STREAM
  duration_s     INTEGER
  -- file
  rel_path       TEXT                 -- relative to music root
  format         TEXT
  -- stream
  source_url     TEXT
  stream_url     TEXT
  expires_after  INTEGER              -- epoch seconds; indexed
  -- state
  failing        INTEGER NOT NULL DEFAULT 0
  error_kind     TEXT
  error_message  TEXT
  error_at       INTEGER
  error_reporter TEXT

track_metadata
  track_id  TEXT NOT NULL REFERENCES track(id) ON DELETE CASCADE
  key       TEXT NOT NULL
  value     TEXT NOT NULL
  ord       INTEGER NOT NULL          -- value order within key
  PRIMARY KEY (track_id, key, ord)

playlist
  id            TEXT PRIMARY KEY
  name          TEXT NOT NULL
  playback_mode TEXT NOT NULL DEFAULT 'SEQUENTIAL'
  created_at    INTEGER NOT NULL
  updated_at    INTEGER NOT NULL

playlist_entry
  playlist_id TEXT NOT NULL REFERENCES playlist(id) ON DELETE CASCADE
  position    INTEGER NOT NULL
  track_id    TEXT NOT NULL REFERENCES track(id)
  PRIMARY KEY (playlist_id, position)

playback_state
  session_id         TEXT PRIMARY KEY  -- 'default' until sessions exist
  active_playlist_id TEXT REFERENCES playlist(id)
  current_track_id   TEXT REFERENCES track(id)
  transport_state    TEXT NOT NULL
  position_seconds   INTEGER NOT NULL DEFAULT 0
  position_origin    TEXT               -- OBSERVED | REPORTED
  updated_at         INTEGER NOT NULL

CREATE INDEX idx_track_expires_after ON track(expires_after)
  WHERE kind = 'STREAM' AND expires_after IS NOT NULL;
```

### D8. No singleton playback state — session-keyed from day one

`playback_state` is keyed by `session_id`; all current API traffic implicitly
uses the `default` row, preserving today's "all clients see the same state"
behavior. Future user sessions become additional rows with zero schema change —
the API just starts resolving a real session identifier. This is deliberately
cheap now and avoids a migration later.

### D9. Per-playlist playback mode

Mode moves from a global field to `playlist.playback_mode`. New playlists get
`SEQUENTIAL`; user changes are written back to the playlist row (remembered, not
session-scoped). No separate session-level override — one nullable column on
`playback_state` can add that later if demand appears.

### D10. PLAYING→PAUSED normalization and explicit client init

Transport state is persisted truthfully, but a `PLAYING` state with no attached
client is a phantom. The server transitions persisted/restored `PLAYING` to
`PAUSED` in two situations:
1. **Server restart** — state restored from DB resumes paused at the saved
   position ("here's where you left off").
2. **Client init** — a fresh client explicitly calls an init action while the
   server believes it is `PLAYING`; the server normalizes to `PAUSED` at the
   recorded position and returns the snapshot.

Init is a **distinct action from reading state**, exposed as its own endpoint
(e.g. `POST /api/playback/init`), not a flag on the state read. `GET
/api/playback` is a pure read with no normalization and no side effects. The
init endpoint is the future home of session init/restore: when sessions arrive,
the same endpoint binds the caller to a session row (D8) — clients adopt it
from day one and gain sessions without an API change.

Client *reconnect* semantics for an active session are unchanged.

### D11. Source-aware streaming: server-side proxy with refresh fallback

`GET /api/stream/{trackId}` is the single byte endpoint for all track kinds:

```
resolve(trackId)
 ├─ FILE   → open musicRoot/rel_path, stream with Range support (unchanged path)
 └─ STREAM → streamUrl fresh?
              ├─ yes        → proxy bytes from streamUrl (forward client Range
              │               headers when present; pass through status codes)
              └─ no/expired → StreamResolver.refreshStream(track) via SPI
                              ├─ success → persist new streamUrl/expiresAfter,
                              │            clear STREAM_EXPIRED state, serve
                              └─ failure → mark failing (STREAM_UNREACHABLE),
                                           502/503 to client
```

Proxying (over 302 redirect) keeps `streamUrl` hidden, lets the server observe
stream health directly, and applies refresh/state logic at exactly one seam.
Clients see no difference between file and stream tracks.

**Latency and fault tolerance**: a refresh is an upstream network call that
typically takes hundreds of milliseconds, may take several seconds, and rarely
more. The stream-request path must therefore: run refresh with a bounded
timeout and limited retries (never block a request thread indefinitely); allow
at most one in-flight refresh per track (concurrent requests for the same
expired track share one refresh); and never let a failed refresh corrupt
previously persisted state. The preemptive scheduler (D12) exists precisely to
keep this slow path off the hot path.

### D12. Preemptive stream refresh scheduler

A scheduled task (Spring `@Scheduled`, fixed delay) finds stream tracks expiring
within a configurable window — but **scoped to the active playlist plus the
next-track candidate**, not the whole registry, to avoid hammering third-party
services. Configuration knobs (with defaults):

```
gravifon.streams.refresh-ahead        = PT1H   (refresh when expiry within 1h)
gravifon.streams.refresh-interval     = PT10M  (scheduler cadence)
gravifon.streams.refresh-scope        = ACTIVE_PLAYLIST_PLUS_NEXT
```

Kept configurable on purpose: real-world behavior against real services should
drive tuning without redeploys. The in-request refresh (D11) is the safety net
when the scheduler hasn't run or the scope excluded the track.

### D13. Resolver SPI: list-in, list-out

Source URLs are **collection-ambiguous**: one input URL may resolve to any
number of tracks (a track page → 1 track; an album page → N tracks; an artist
or label page → many albums × tracks). The SPI and the API are therefore shaped
list-in/list-out from the start:

```java
interface StreamResolver {
    boolean supports(String sourceUrl);                   // e.g. by host
    SourceUrlNormalizer normalizer();                     // optional stricter normalization
    List<ResolvedTrack> resolveTracks(String sourceUrl);  // 1..N prototypes: metadata, duration
    ResolvedStream refreshStream(Track track);            // fresh streamUrl + expiresAfter
}

interface TagWriteBackHandler {       // file tag writes plug in later
    boolean supports(Track track);
    WriteBackResult writeBack(Track track, MetadataPatch patch);
}
```

`resolveTracks` returns track *prototypes* (per-track source URL, metadata,
duration if known); the registry derives IDs (D2), dedupes against existing
tracks, and persists new ones. Adding one URL may thus create many tracks;
adding a list of URLs may create many more. This change ships the SPI, the
registry integration, and **zero** concrete resolvers (streams without a
resolver fail refresh with `STREAM_UNREACHABLE`), plus a **no-op** file
write-back handler (library stays read-only; write-back is a server-side
concern transparent to clients).

### D14. API surface deltas

- `GET /api/tracks/{id}` — full track document: kind, metadata map, duration,
  state, source info (stream: `sourceUrl` + expiry, never `streamUrl`).
- `PATCH /api/tracks/{id}/metadata` — merge/replace metadata keys; fans out
  everywhere via shared identity; triggers write-back handler.
- `POST /api/tracks/{id}/state` — client failure report (`failing`, error kind,
  message); also supports explicit clear.
- Playlist mutations: `POST /api/playlists` accepts entries referencing existing
  track IDs, stream source URLs (resolved list-in/list-out per D13), or a
  catalog-materialization descriptor (D6); `PATCH /api/playlists/{id}` (rename,
  reorder, add/remove entries, set mode); `DELETE /api/playlists/{id}`.
- `GET /api/stream/{trackId}` — kind-agnostic byte endpoint (D11).
- Playback endpoints unchanged in shape; state responses unchanged in shape
  (session keying is implicit).

## Component Model and Testability

The design deliberately decomposes into narrow, single-responsibility components
with explicit seams, per the project's engineering design rule. Target unit
granularity — each component unit-testable in isolation with minimal mocking:

```
TrackRegistry          ─ identity derivation, track CRUD, state transitions
                         (pure domain logic; persistence behind repository iface)
LibraryScanner         ─ filesystem walk + file facts (filesystem behind an
                         interface → Jimfs in-memory FS in tests)
MetadataExtractor      ─ audio tags + duration → metadata map (jaudiotagger
                         adapter; mockable interface)
ScanReconciler         ─ pure merge policy: (registry snapshot × scan result)
                         → create/refresh/tombstone/remove decisions
PlaylistService        ─ playlist CRUD, ordering, materialization descriptors
PlaybackService        ─ state machine over persisted context (clock injectable)
StreamRefreshScheduler ─ scope selection + timing policy (clock injectable)
StreamProxy            ─ upstream fetch, range forwarding, error mapping
StreamResolverRegistry ─ resolver routing by supports()
SourceUrlNormalizer    ─ per-source URL normalization (default + overrides)
```

Testing strategy:
- Pure-policy components (`ScanReconciler`, identity derivation, refresh-scope
  selection, transport normalization) are tested as plain units — no Spring,
  no DB, no filesystem.
- Filesystem-dependent components are tested against Jimfs
  (`com.google.jimfs`) in-memory filesystems; filesystem access is injected,
  never static.
- Repositories: Spring Data JPA adapter tests against real SQLite files in tmp
  dirs.
- Stream proxy: tests with a stubbed upstream (MockWebServer) covering
  fresh/expired/refresh-failure/timeout/slow-refresh/range-passthrough.
- Playback normalization: integration test restarting the Spring context
  against a pre-seeded DB.
- Both golden paths and edge cases (empty registry, missing files, expired
  URLs with slow resolvers, concurrent refresh of the same track, malformed
  metadata, unknown-track references in mutations) are first-class test
  targets — discovering them here is cheaper than during client integration.
- Coverage gate unchanged: ≥80% line on business logic via `mvn verify`.

## Data Flows

**Startup**: Flyway migrate → load registry from DB → filesystem scan →
reconcile per D5 → restore `playback_state` (default session), normalizing
PLAYING→PAUSED → scheduler starts.

**Add stream source(s)**: client POSTs playlist entries containing source
URL(s) → matching resolver expands each URL to 1..N track prototypes (D13) →
registry derives IDs and dedupes → new tracks persisted with supplied metadata
(and duration when known) → playlist references the resulting IDs → stream
URLs are fetched lazily at stream time or via scheduler when a track becomes
relevant.

**Client metadata edit**: `PATCH /api/tracks/{id}/metadata` → update
`track_metadata` rows transactionally → write-back handler (no-op today) → all
playlists see the change on next read.

**Track fails mid-play**: client gets 5xx from `/api/stream/{id}` or playback
error → client `POST /api/tracks/{id}/state` → server records error → client
decides to skip (calls next-track) — server takes no skip initiative.

## Migration

Breaking change with no external consumers. The old in-memory model has no
persisted data to migrate. On first boot with this change, Flyway creates the
schema and the scan populates the registry from scratch. The existing reference
SPA static assets are removed rather than migrated; the SPA will be rewritten
from scratch against the new API (see Non-Goals).
