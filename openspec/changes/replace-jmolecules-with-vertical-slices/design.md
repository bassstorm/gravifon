# Design

## Context

See [proposal.md](./proposal.md) for motivation and scope.

Phase 1 (`introduce-client-sessions`) completed the selective reset—removing obsolete `playlist` and `playback` code—and introduced durable users, client sessions, and shared stream registration. Before Phase 2 and Phase 3 (`redesign-playlist-and-playback-queue`) rebuild `playlist` and `playback`, **Phase 1.5** removes jMolecules (`jmolecules-ddd`, `jmolecules-onion-architecture`, and `jmolecules-archunit`) and establishes enforced vertical package slices, YAGNI single-implementation conventions, and a hybrid cross-slice interaction model while the codebase is at its smallest footprint.

## Goals / Non-Goals

**Goals:**

- Remove jMolecules dependencies, `package-info.java` ring annotations, class-level `@DomainRing` / `@ApplicationRing` / `@InfrastructureRing` annotations, and `AggregateRoot` / `Identifier` marker interfaces.
- Enforce both **vertical package slice boundaries** (`session`, `registry`, `playlist`, `playback`, `streaming`) and **horizontal package purity** (`model`, `identity`, `service`, `repository`, `persistence`, `api`) through plain ArchUnit rules in `DomainArchitectureArchUnitTest`.
- Apply YAGNI to internal implementations: collapse redundant 1:1 `*Repository` + `Jpa*Repository` pairs into single concrete `@Repository` classes in `<slice>/repository` that expose only domain types, while retaining interfaces where multiple implementations or plug-ins exist.
- Define a hybrid cross-slice communication model combining **Spring Application Events** (for push state changes) and **Multi-Bean Collection Injection** (for pull aggregation and strategy selection).
- Clearly separate architectural documentation by audience: `README.md` provides the high-level slice map and interaction overview for human readers; `AGENTS.md` provides exact package, dependency, YAGNI, and verification rules for agent/developer execution.

**Non-Goals:**

- Split the project into multiple Maven `<modules>` (`pom.xml` per slice), which would complicate the shared H2 schema (`V1__init.sql`), cross-table pessimistic lock ordering, `@DataJpaTest` slices, JaCoCo bundle coverage, and Docker layer caching.
- Change any public HTTP endpoint, database table, or Phase 1 runtime behavior.
- Implement Phase 2 (`playlist` / `playback` queue) or Phase 3 (playback protocol) features in this change.

## Decisions

### 1. Enforced Vertical Package Slices + Horizontal Package Purity (Plain ArchUnit)

Instead of relying on manual `@DomainRing` / `@ApplicationRing` / `@InfrastructureRing` annotations (which silently skip unannotated classes and ignore cross-slice imports within the same ring), `DomainArchitectureArchUnitTest` enforces architecture directly from package structure:

```text
+-----------------------------------------------------------------------+
|                           api / observability                         |
|         (controllers, interceptors, request/response DTOs,            |
|          exception translation, correlation filter)                   |
+-----------------------------------------------------------------------+
       |                 |                  |                  |
       v                 v                  v                  v
+-------------+   +-------------+   +--------------+   +----------------+
|  streaming  |   |  playback   |   |   playlist   |   |    registry    |
|  (delivery  |-->|  (context,  |-->| (playlists,  |-->| (shared tracks,|
|  & upcoming |   |   queue,    |   |  entries,    |   |  scan, stream  |
|   refresh)  |   |  protocol)  |   |  mode)       |   |  registration) |
+-------------+   +-------------+   +--------------+   +----------------+
       |                 |                  |                  |
       |                 v                  v                  |
       |          +--------------------------------+           |
       +--------->|            session             |           |
                  |  (users, client sessions,      |           |
                  |   caller, inactivity events)   |           |
                  +--------------------------------+           |
                         |                  |                  |
                         v                  v                  v
+-----------------------------------------------------------------------+
|                       persistence / config / error                    |
|     (JPA @Entity classes, Spring Data Jpa*Store interfaces,           |
|      admission locks, @ConfigurationProperties, domain exceptions)    |
+-----------------------------------------------------------------------+
```

#### Vertical slice dependency rules (`com.gravifon.player.<slice>..`)

| Slice | Allowed dependencies on other slices | Must not depend on |
|---|---|---|
| `session` | `persistence`, `config`, `error` | `registry`, `playlist`, `playback`, `streaming`, `api`, `observability` |
| `registry` | `persistence`, `config`, `error` | `session`, `playlist`, `playback`, `streaming`, `api`, `observability` |
| `playlist` | `session.model`, `session.repository`, `registry.model`, `registry.repository`, `persistence`, `config`, `error` | `playback`, `streaming`, `api`, `observability` |
| `playback` | `playlist.model`, `session.model`, `session.repository`, `session.service`, `registry.model`, `registry.repository`, `persistence`, `config`, `error` | `playlist.service`, `playlist.repository`, `streaming`, `api`, `observability` |
| `streaming` | `registry`, `playback`, `session.model`, `config`, `error` | `playlist`, `persistence`, `api`, `observability` |
| `persistence` | None of the vertical slices (`session`, `registry`, `playlist`, `playback`, `streaming`), `api`, `config`, `observability` | All domain/application/API packages |
| `api` | `session`, `registry`, `playlist`, `playback`, `streaming`, `config`, `error`, `observability` | `persistence` |

#### Horizontal package purity rules

1. **Domain model packages (`..model..`, `..identity..`)**:
   - Must not depend on `org.springframework..`, `jakarta..`, `org.jaudiotagger..`, `lombok..`, or any `..service..`, `..repository..`, `..persistence..`, `..api..`, `..config..`, `..observability..`, `..streaming..`, `..scan..`, `..metadata..`, or `..stream..` package.
2. **Application service packages (`..service..`, `..scan..`, `..stream..`)**:
   - Must not depend on `..api..`, `..persistence..` (JPA `*Entity` or `Jpa*Store`), or `jakarta.servlet..`.
3. **Repository packages (`..repository..`)**:
   - Encapsulate `persistence` (`*Entity` and `Jpa*Store`) behind domain-typed methods. Public method signatures (parameters and return types) of classes in `..repository..` must not expose `com.gravifon.player.persistence..` or `org.springframework.data..` types.

### 2. YAGNI Repository and Service Simplification

Eliminate 1:1 pass-through interfaces that exist only for architectural ceremony:

- **Single-implementation rule**:
  - Do not introduce a separate `*Repository` interface + `Jpa*Repository` adapter (or `*Policy` interface + `Default*Policy` implementation) when there is only one implementation.
  - Define each slice repository directly as a concrete `@Repository public class <Name>Repository` in `<slice>/repository` whose public methods accept and return only domain types while encapsulating `persistence/*Entity` and `persistence/Jpa*Store` internally.
  - Keep `persistence/*Entity` and `persistence/Jpa*Store` separate from domain `record`s because JPA requires mutable entity state while the domain model uses immutable Java 21 records.
- **When interfaces are justified (genuine polymorphism or boundary plug-ins)**:
  - Multiple runtime implementations or cross-slice contributors (`StreamResolver` in `List<StreamResolver>`, `ReferencedTrackIdsPort` in `List<ReferencedTrackIdsPort>`).
  - External I/O or filesystem boundary abstractions that have distinct test/fixture or no-op implementations (`LibraryScanner`, `MetadataExtractor`, `TagWriteBackHandler`, `StreamProxy`, `MusicLibraryProbe`).

### 3. Hybrid Cross-Slice Communication Model

Cross-slice collaboration uses the simplest standard Spring capability that matches the interaction shape:

| Interaction shape | Mechanism | Architectural role |
|---|---|---|
| **Atomic in-transaction state reaction** (producer does not know consumers; all changes commit or roll back together under caller's DB locks) | Synchronous `@EventListener` via `ApplicationEventPublisher` | Decouples foundational or upstream slices from downstream slices that must react atomically within the same transaction (e.g., session invalidation or source-playlist changes). |
| **Best-effort post-commit broadcast** (non-blocking side effects after commit) | `@TransactionalEventListener(phase = AFTER_COMMIT)` | Reserved for non-transactional side effects after commit (e.g., background warm-up or external notifications) so slow I/O never holds DB locks or rolls back state mutations. |
| **Pull aggregation across slices** (caller waits for and merges contributions from all slices) | `List<Port>` multi-bean injection | Allows an upstream coordinator to collect data from 0..N slices without depending on them. `LibraryScanCoordinator` injects `List<ReferencedTrackIdsPort>` and unions all returned `Set<String>` track IDs; when no slice registers a provider, Spring injects an empty list without requiring a no-op stub. |
| **First-wins strategy selection** (single consumer handles request and returns result) | Ordered `List<Strategy>` injection | Selects the first strategy bean supporting a request (e.g., `StreamResolverRegistry` iterating `List<StreamResolver>`). |

### 4. Audience-Specific Architecture Documentation

- **`README.md` (Human readers)**: Provides a concise, scannable architectural overview—the vertical slice diagram, each slice's responsibility, and how slices collaborate—without implementation-level rules or test-slice recipes.
- **`AGENTS.md` (Agent/developer implementors)**: Provides authoritative, actionable engineering rules—the vertical slice dependency matrix, horizontal purity constraints, YAGNI single-implementation repository rule, cross-slice event/provider rules, and verification commands—aligned 1:1 with `DomainArchitectureArchUnitTest`.

## Verification Strategy

- Run `DomainArchitectureArchUnitTest` to verify all vertical and horizontal package boundaries.
- Run all unit, `@DataJpaTest`, `@WebMvcTest`, and `@SpringBootTest` suites (`mvn spotless:apply`, `mvn clean test`, `mvn clean verify`) to confirm zero behavioral regressions and ≥80% JaCoCo line coverage.
