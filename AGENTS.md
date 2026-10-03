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

### Persistence

- Configure datasources declaratively with Spring Boot `spring.datasource.*` properties; avoid custom `DataSource` beans for database setup.
- Use H2 for runtime persistence with its file stored under `GRAVIFON_CONFIG_DIR` (default `/config`), and use an isolated in-memory H2 database for tests.
- Flyway owns schema creation and evolution. Keep Hibernate at `ddl-auto: validate`; do not use Hibernate schema creation or updates.
- Keep domain repository ports separate from Spring Data `JpaRepository` stores, with persistence adapters implementing the domain ports.
- Use `@DataJpaTest` with Flyway and Hibernate schema validation to verify entity mappings; do not bypass datasource replacement or import custom datasource configuration.

### Test Isolation and Boundaries

- Use `@Transactional` test rollback for integration tests that write to the database; reset in-memory singleton state separately when needed.
- Keep test datasources in-memory through `application-test.yml` and avoid redundant JPA property overrides in test annotations.
- Verify persistence through domain repositories and Spring Data JPA queries; do not use `JdbcTemplate`, raw SQL, or manual `EntityManager.flush()` calls in tests.
- Use `@ExtendWith(MockitoExtension.class)` with `Strictness.STRICT_STUBS` for plain service unit tests, and stub only the scenario-specific repository results each test consumes.
- Do not simulate repository filtering or persistence behavior in Mockito answers; test domain invariants directly and persistence boundaries with JPA tests.

### Controller Boundaries, Validation, and Errors

- Keep controllers strictly presentation-focused: handle routing, request decoding, and status translation without opening transaction boundaries.
- Encapsulate multi-step mutations and aggregate orchestration in atomic, `@Transactional` application-service methods rather than chaining operations in controllers.
- Separate presentation DTOs from application contracts: keep request models in the infrastructure ring and map them to application commands before invoking services.
- Validate request inputs declaratively at the boundary using Bean Validation annotations (`@Valid`) to reject malformed or invalid payloads before reaching business logic.
- Use dedicated request DTOs for create and update operations; keep operation-specific invariants such as catalog exclusivity authoritative in the application service.
- Model conditional track-failure reporting without null-bypass regex constraints, and expose explicit service operations for reporting failures and clearing them.
- Apply container-element constraints to nested payload collections and validate defensively in application services so invalid direct calls fail explicitly rather than with `NullPointerException`.
- Bind domain enums directly to request parameters and path variables using their canonical uppercase names.
- Keep domain logic free of HTTP semantics: domain exceptions represent missing entities or business rule violations, which the presentation exception handler maps to appropriate HTTP status codes (e.g. missing entities to 404, input contract violations to 400).

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

Before marking any task done (when editing Java sources, apply formatting first):

```bash
mvn spotless:apply
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
openspec validate --all --no-interactive --json
mvn clean verify
```

Agents MUST use explicit, non-interactive OpenSpec command options. In
particular, do not run bare `openspec validate`, because it prompts for the
validation scope. Use `openspec validate --all --no-interactive` or an explicit
scope such as `--changes` or `--specs`; add `--json` when machine-readable
output is useful.

The Dev Container uses the Docker-in-Docker feature with an isolated Docker
daemon. It does not mount the host Docker socket. Docker CLI, Docker Compose,
image builds, and container-sensitive verification are supported inside the
Dev Container. Rebuild or recreate the Dev Container after changing its
features. Docker state belongs to the nested daemon and is separate from host
containers, images, networks, and volumes.

Use the Dev Container for the complete verification workflow:

```bash
mvn clean test
mvn clean verify
docker compose config
docker compose down -v
docker compose up --build
```

Host mode is only required for release workflows that intentionally publish or
deploy through the host Docker daemon; it is not needed for normal development
or verification.
