# Gravifon

Local-network audio player

## Architecture

```
Browser (SPA)
  │  REST + Streaming  │  CorrelationId header
  ▼                    ▼
Spring Boot app
  ├── /api/tracks          – catalog metadata
  ├── /api/playlists       – in-memory playlists
  ├── /api/playback        – server-maintained playback context (track/playlist/mode/position)
  └── /api/stream/{id}     – zero-copy FileChannel byte-range streaming
```

**Bounded contexts:** `catalog` → `playlist` → `playback` → `streaming` → `api`
Transport state is client-managed. Server state is in-memory and resets on restart.

## Build and verify

```bash
mvn clean verify
```

Line coverage ≥ 80% on business logic is enforced by JaCoCo during `verify`.

## Run locally

```bash
GRAVIFON_MUSIC_ROOT=/path/to/music mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open `http://localhost:8080`.

## Run in a container

```bash
mvn clean verify -Pdocker
docker compose up
```

The Docker profile builds the image locally; Compose consumes it. Runtime configuration (mounts, ports, environment) is defined in the containerized-runtime spec.

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
