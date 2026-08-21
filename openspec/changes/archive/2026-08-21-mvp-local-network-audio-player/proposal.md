## Why

Gravifon needs a releasable MVP that can run as a Dockerized local-network audio player and prove the end-to-end product shape. Building this now establishes a stable, client-agnostic server contract and container deployment so future phases (playlist authoring, richer clients, custom formats, persistence) can iterate safely. The server is the sole owner of playback state — including transport intent and position — so that state is naturally restorable across client reconnects with no session management, and the REST contract itself must stay agnostic to any particular client's playback architecture so alternative clients (including a fully custom decode/buffering pipeline) can drive it unchanged. This iteration is intentionally server-only: it focuses on getting the playback state machine and REST contract right, backed by thorough unit and Spring integration testing, before any client is built against it. A dedicated follow-up change will propose the browser client once the server contract has proven itself through that testing.

## What Changes

- Deliver a Spring Boot 3 / Java 21 server with in-memory playback context (active playlist, current track, mode, transport state, position) for a mounted `/music` library, restorable by any reconnecting client for as long as the server process keeps running.
- Scan hierarchical media folders for supported audio files (`.flac`, `.mp3`, `.ogg`) and bootstrap at least one default playlist containing all discovered tracks.
- Expose a minimal, entity-reference-driven REST surface for playback control (track/playlist selection, mode, transport, server-driven next-track advance, optional position reporting), track metadata retrieval, and playlist/track CRUD surfaces (with MVP-scoped limitations documented), keeping data retrieval and state manipulation clearly separated.
- Explicitly scope MVP to a single-user local-network scenario with no authentication/authorization and no server-side session management.
- Stream original audio bytes without transcoding, including HTTP range support for range-based audio playback clients.
- Add correlation-aware, structured server logging for troubleshooting.
- Package the app as a Docker image runnable via a committed `docker-compose.yaml` on local machines (default host mount `~/Library` -> `/music`) and as a standalone container (e.g., NAS deployments).
- Add and align documentation for agent-first development workflows (OpenSpec + repository guidance).
- Defer the browser client entirely to a follow-up change; this change ships no new client-facing UI work.

## Capabilities

### New Capabilities
- `media-library-catalog`: Discover supported audio files from mounted `/music`, extract MVP metadata, and expose catalog data.
- `playlist-management`: Maintain server-side playlists in memory, including default all-tracks playlist and active playlist selection.
- `playback-control`: Manage server-side playback context (playlist/track/mode/transport state/position), server-driven next-track selection, eager re-pick on playlist switch, and optional-report-with-fallback position tracking.
- `audio-streaming`: Serve audio content for playback with byte-range support and no transcoding.
- `player-rest-api`: Provide flexible REST endpoints for playback, tracks, playlists, and current-state queries suitable for future clients.
- `observability-and-correlation`: Enforce `CorrelationId` request propagation and comprehensive server-side logs.
- `containerized-runtime`: Define Docker image and Compose runtime conventions for local/NAS operation with mounted music storage.
- `agent-first-project-documentation`: Document architecture, design/testing principles, and OpenSpec/agent workflow conventions.

### Modified Capabilities
- None.

## Impact

- Affects backend packages under `src/main/java/com/gravifon/player` for catalog, playlists, playback, streaming, API, config, and logging.
- Does not add or update frontend assets; existing static assets under `src/main/resources/static` are left as-is (stale against the reworked API) until the follow-up client change lands.
- Updates deployment assets (`Dockerfile`, Compose wiring, runtime env/mount docs) for image-based delivery.
- Expands OpenSpec artifacts and project guidance (`AGENTS.md`, `openspec/config.yaml`, and supporting docs).
- Introduces/updates tests for catalog scanning, playlist/playback flows, streaming range behavior, and API contract validation.

