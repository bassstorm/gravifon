## Context

See [proposal.md](proposal.md) for motivation and problem analysis.

The system currently couples domain entities with infrastructure concerns:
- `Track` is a monolithic record combining mutually exclusive fields for files and streams.
- Services and repositories use multi-constructor patterns to inject `Clock` purely for unit testing.
- `StreamProxy` and `HttpStreamProxy` live inside `com.gravifon.player.registry.stream` and interact directly with Jakarta Servlet objects.
- `AudioStreamingController` performs low-level file I/O, range parsing, and content-range header formatting.
- `TrackRegistry` is a monolithic service orchestrating startup disk scans, playlist references, tag write-backs, and error reporting.
- `PlaylistService` executes $O(N)$ catalog scans via `trackRepository.findAll()` to validate track IDs on every playlist modification.

## Goals / Non-Goals

**Goals:**
- Apply jMolecules DDD stereotypes (`@AggregateRoot`, `@Entity`, `@ValueObject`, `@DomainService`, `@ApplicationService`, `@Repository`) to clarify structural roles.
- Establish architectural boundaries enforced via ArchUnit tests (`JMoleculesDddRules`).
- Refactor `Track` into a Java 21 sealed interface hierarchy (`FileTrack`, `StreamTrack`) and introduce strongly typed identifiers (`TrackId`, `PlaylistId`).
- Eliminate `Clock` dependencies and dual constructors across all components; pass explicit `Instant` timestamps to pure policy methods.
- Separate streaming delivery (`com.gravifon.player.streaming`) and file range streaming from catalog registration and controllers.
- Extract library scanning and startup reconciliation from `TrackRegistry` into a dedicated application service.
- Optimize repository query methods (`existsById`, `findAllById`) to avoid full-catalog memory loading during playlist operations.

**Non-Goals:**
- Modifying REST API schemas or client-facing HTTP response structures.
- Changing Flyway database schemas or underlying SQLite table definitions.
- Introducing asynchronous/reactive streaming (retaining existing synchronous byte transfer).
- Rewriting Jaudiotagger metadata extraction internals.

## Decisions

### 1. jMolecules DDD Stereotypes & Onion Architecture Enforcement
- **Decision**: Introduce `org.jmolecules:jmolecules-ddd`, `org.jmolecules:jmolecules-onion-architecture`, and `org.jmolecules.integrations:jmolecules-archunit`. Use ring annotations (`@DomainRing`, `@ApplicationRing`, `@InfrastructureRing`) and DDD interfaces to classify domains, application services, and infrastructure adapters. Enforce `JMoleculesArchitectureRules.ensureOnionSimple()` and domain package isolation.
- **Rationale**: Onion architecture naturally captures DDD dependency inversion where repository interfaces are domain ports and JPA stores are infrastructure adapters. Dependencies point strictly inward: Domain is pure and isolated, Application coordinates use-cases without depending on Infrastructure, and Infrastructure adapters implement domain ports and handle web/storage I/O.
- **Alternatives Considered**:
  - *Classical 4-tier layered architecture (`ensureLayering()`)*: Clashes with DDD repository implementations because in classical layering, infrastructure is at the bottom and cannot access domain models.
  - *No formal architecture annotations*: Relies purely on developer discipline, which previously led to architecture drift.

### 2. Sealed Polymorphic Track Model with Strongly-Typed Identifiers
- **Decision**: Define `Track` as a sealed interface in `com.gravifon.player.registry.model` with implementations `FileTrack` and `StreamTrack`. Introduce `TrackId` and `PlaylistId` as immutable value objects.
- **Rationale**: Replaces nullable, mutually exclusive fields with compiler-enforced type safety. Stream refresh policies and resolvers operate directly on `StreamTrack`, eliminating defensive type checks and casting.
- **Alternatives Considered**:
  - *Retaining single Track record with helper methods*: Leaves invalid state combinations possible at runtime.
  - *Abstract class hierarchy*: More verbose than Java 21 records and doesn't leverage pattern matching as naturally.

### 3. Removal of Clock Dependencies in Favor of Pure Policy Methods
- **Decision**: Remove `Clock` from all constructors and fields (`StreamRefreshScheduler`, `StreamRefreshService`, `JpaPlaylistRepository`, `JpaPlaybackStateRepository`). Where time evaluation is required (e.g. freshness checks, threshold look-ahead), pass explicit `Instant evaluationTime` parameters. For persistence timestamps, use `Instant.now()` at the boundary.
- **Rationale**: Eliminates dual-constructor anti-patterns, makes domain functions pure and deterministic, and removes Mockito clock scaffolding from tests.
- **Alternatives Considered**:
  - *Global `@Bean Clock`*: Still couples domain services to time-supplier interfaces when simple parameter passing makes methods pure and directly unit-testable.

### 4. Separation of Audio Streaming Delivery from Catalog Registry
- **Decision**: Move `StreamProxy` and `HttpStreamProxy` out of `com.gravifon.player.registry` into `com.gravifon.player.streaming`. Extract byte-range file streaming logic out of `AudioStreamingController` into a dedicated `AudioStreamingService`.
- **Rationale**: The registry domain is responsible for catalog identity, metadata, and persistence. HTTP proxying, byte-range math, and servlet output streaming are transport/delivery concerns.
- **Alternatives Considered**:
  - *Leaving proxies in `registry.stream`*: Perpetuates coupling between catalog metadata and web/servlet streaming.

### 5. Decomposing TrackRegistry and Startup Scan
- **Decision**: Extract startup scanning and scan reconciliation into `LibraryScanCoordinator` (an Application Service). `TrackRegistry` remains focused on managing the catalog aggregate roots. Decouple `TrackRegistry` from direct `PlaylistRepository` queries.
- **Rationale**: Adheres to Single Responsibility. Prevents `@PostConstruct` from blocking application initialization with heavy I/O in the registry service.
- **Alternatives Considered**:
  - *Keeping scan inside TrackRegistry*: Continues the "God class" anti-pattern.

### 6. Repository Optimization for Playlist Operations
- **Decision**: Add `boolean existsById(TrackId id)` and `List<Track> findAllById(Iterable<TrackId> ids)` to `TrackRepository` and `JpaTrackStore`. Update `PlaylistService.validateTracks` to verify IDs without loading the entire catalog.
- **Rationale**: Eliminates memory-intensive full-table scans ($O(N)$ catalog size) whenever a playlist is created or modified.

### 7. Constructor Consolidation & Boilerplate Reduction with Lombok
- **Decision**: Eliminate all secondary/test-specific constructors across services, repositories, and controllers. Use Lombok (`@RequiredArgsConstructor` / `@AllArgsConstructor`) on classes where appropriate to eliminate constructor boilerplate and maintain uniform dependency injection.
- **Rationale**: With testing seams cleanly separated and artificial clock dependencies removed, classes require only one canonical constructor. Using Lombok aligns with existing project conventions and keeps service declarations concise.

## Risks / Trade-offs

- **[Risk] Wide blast radius across existing tests**: Changing `Track` to a sealed hierarchy and introducing `TrackId` touches many call sites.
  - *Mitigation*: Perform refactoring in incremental, compilable phases starting with domain models and repositories, updating test fixtures step-by-step.
- **[Risk] JPA Entity Mapping Complexity**: JPA `TrackEntity` uses a single-table strategy with a `kind` discriminator column.
  - *Mitigation*: Keep `TrackEntity` as a single table. The repository mapper (`JpaTrackRepository`) handles mapping `TrackEntity` to `FileTrack` or `StreamTrack` cleanly using pattern matching.
