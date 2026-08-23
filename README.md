# Gravifon

Local-network audio player

## Architecture

```
Browser (SPA)
  │  REST + Streaming  │  CorrelationId header
  ▼                    ▼
Spring Boot app
  ├── /api/tracks          – catalog metadata
  ├── /api/playlists       – persistent playlists
  ├── /api/playback        – server-maintained playback context (track/playlist/mode/position)
  └── /api/stream/{id}     – zero-copy FileChannel byte-range streaming
```

**Bounded contexts:** `registry` → `playlist` → `playback` → `streaming` → `api`
The registry, playlists, and playback context are stored in SQLite. `GET /api/playback`
is read-only; a client initializes playback with `POST /api/playback/init`.

## Build and verify

```bash
mvn clean verify
```

Line coverage ≥ 80% on business logic is enforced by JaCoCo during `verify`.

## Development container

The recommended development environment is the Dev Container in `.devcontainer/`.
It provides Java 21, Maven, Node 22, OpenSpec, Docker CLI, Docker Compose, and
an isolated Docker-in-Docker daemon. Rebuild or recreate the Dev Container after
changing its features.

Run the complete verification workflow from inside the Dev Container:

```bash
mvn clean test
mvn clean verify
docker compose config
docker compose down -v
docker compose up --build
```

The nested daemon has its own containers, images, networks, and volumes; it does
not use the host Docker socket or share host Docker state. This development
environment behavior is separate from the application container runtime
described below.

## Run locally

```bash
GRAVIFON_LIBRARY_DIR=/path/to/music mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open `http://localhost:8080`.

## Run in a container

```bash
mvn clean verify -Pdocker
docker compose up
```

The Docker profile builds the image locally; Compose consumes it. Runtime configuration (mounts, ports, environment) is defined in the containerized-runtime spec.

The container uses `/music` for the read-only library and `/config` for the SQLite
database (`gravifon.db`). Compose uses Docker-managed volumes by default, so no
host directory is assumed or tracked. Set `GRAVIFON_LIBRARY_PATH` to a host music
directory (for example, `~/Library`) and `GRAVIFON_CONFIG_PATH` to a host config
directory when persistence outside Docker is required. Both variables also accept
named volumes. The application paths are `/music` and `/config`, configurable with
`GRAVIFON_LIBRARY_DIR` and `GRAVIFON_CONFIG_DIR` when running outside Compose.

For a host-backed persistence check:

```bash
GRAVIFON_LIBRARY_PATH="$HOME/Library" \
GRAVIFON_CONFIG_PATH="$(mktemp -d)" \
docker compose up --build
```

## Conventions

- **Testing:** JUnit 5 + AssertJ + Mockito + Spring Boot test slices; use `@MockitoBean`
- **Clean verification before merge:** `mvn clean test` and `mvn clean verify`; for container-sensitive checks, `docker compose down -v` then `docker compose up --build`

### Logging severity

| Level | Intent |
|-------|--------|
| `trace` | High-detail troubleshooting; hot-path or repeated diagnostic events |
| `debug` | Diagnostic information useful during development or investigation |
| `info`  | Key lifecycle events (startup complete, track started, playlist selected) |
| `warn`  | Recoverable / unexpected conditions (missing CorrelationId, stalled audio, metadata unavailable) |
| `error` | Actual failures (scan error, audio decode failure, unrecoverable state) |

All server logs include `correlationId` in MDC. Client (SPA) logs follow the same severity table.

## Documentation

This repository is OpenSpec-first: `openspec/specs/` describes the status quo, `openspec/changes/` describes its evolution.
