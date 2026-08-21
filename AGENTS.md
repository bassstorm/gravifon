# Agent Guide

This repository is designed for agent-assisted, spec-driven work.

## Engineering Design Rule

Code should favor high cohesion and narrow, explicit responsibilities at class and method level.
Design components so domain logic can be unit-tested in isolation with minimal mocking and clear behavioral assertions.
Prefer small, composable services and pure transformation methods over large multi-concern units.

## Project Conventions

### Java / Spring Boot

- Java 21 records for immutable models; avoid generating boilerplate Lombok covers (`@Slf4j` for loggers, etc.)
- Declare `@Slf4j` on classes that log; do not write manual `LoggerFactory.getLogger(...)` declarations
- Use `@MockitoBean` in Spring test slices, not the deprecated `@MockBean`
- Coverage gate: ≥ 80% line coverage on business logic, enforced via JaCoCo in `mvn verify`

### Logging severity (server and client)

| Level | Intent |
|-------|--------|
| `trace` | High-detail troubleshooting; hot-path or repeated diagnostics |
| `debug` | Diagnostic information during development or investigation |
| `info`  | Key lifecycle events (startup, track started, playlist selected) |
| `warn`  | Recoverable / unexpected conditions (missing CorrelationId, stalled audio, fallback applied) |
| `error` | Actual failures (scan failure, audio decode error, unrecoverable state) |

All server request logs include `correlationId` in MDC via `CorrelationIdFilter`.

### Correlation lifecycle scope rules

A correlation id represents **one active operation lifecycle**. The rules:

1. **Track playback lifecycle** starts when a track is selected or begins loading. It spans load, metadata, play/pause/seek, buffering, and ended events for that track.
2. **Lifecycle boundary** — mint a new correlation id when:
   - Playback transitions to a different track (auto-next, manual next)
   - Active playlist is switched
   - Playback mode is changed
   - Track is stopped and then replayed (stop → play same track = new lifecycle)
   - Track `ended` event fires (before next play)
3. Non-track operations (playlist switch, mode change) each get their own dedicated lifecycle.
4. On the **client side** (SPA), the correlation id is sent as a `CorrelationId` request header on every API call within that lifecycle so server logs and client logs can be joined.

### Clean verification workflow

Before marking any task done:

```bash
mvn clean test
mvn clean verify
```

For container-sensitive verification:

```bash
docker compose down -v
docker compose up --build
```

## Development Container

The recommended development environment is the Dev Container in `.devcontainer/`.
It provides Java 21, Maven, Node 22, and OpenSpec 1.10.0 without installing Node or OpenSpec on the host.
The repository is mounted read/write, so the same checkout can be opened either normally on the host or in the container.

Use Dev Container mode for Maven, Java, and OpenSpec work:

```bash
openspec list
openspec validate
mvn clean verify
```

The Dev Container deliberately does not mount the Docker socket and does not provide Docker CLI access.
Use normal host mode for application Compose runs, Docker image builds, and release routines:

```bash
mvn clean package -Pdocker
docker compose up
```
