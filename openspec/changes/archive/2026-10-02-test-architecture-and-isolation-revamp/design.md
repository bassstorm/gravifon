## Context

Following the H2 migration in Phase 1, the test suite is functional and fast, but exhibits anti-patterns and boilerplate:
- `GravifonIntegrationTest` manually issues 5 `DELETE FROM` statements in `@BeforeEach` and overrides datasource URLs via dynamic properties to avoid collisions.
- `PersistenceRepositoryDataJpaTest` specifies redundant properties (`spring.jpa.hibernate.ddl-auto=validate`).
- `PlaylistServiceTest` simulates an in-memory repository inside Mockito answers (`findById`, `findAllById` filtering).
- Domain aggregates (`Playlist`, `PlaybackSession`) lack dedicated, fast unit tests covering their state transitions and invariants in isolation.

## Goals / Non-Goals

**Goals:**
- Standardize test database isolation declaratively: eliminate manual `DELETE FROM` in `@BeforeEach` in favor of Spring's standard test transaction rollback (`@Transactional`) or clean context lifecycles.
- Standardize `application-test.yml` configuration and remove redundant annotations/property overrides across test classes.
- Eliminate fake repository simulation in `PlaylistServiceTest` and adopt `@ExtendWith(MockitoExtension.class)` with strict stubbing across unit tests.
- Add pure unit tests for `Playlist` and `PlaybackSession` invariants without Spring context or mocks.
- Verify cross-boundary persistence behaviors (e.g. metadata updates visible across playlists) in real JPA integration tests.
- Update `AGENTS.md` with explicit testing and mocking boundary guidelines.

**Non-Goals:**
- Production code changes or domain API refactoring.
- Controller transaction/validation updates (Phase 3).
- Lifecycle runner and audio streaming updates (Phase 4).

## Decisions

### Decision 1: Declarative Test Transaction Rollback
- **Approach**: In Spring Boot integration tests such as `GravifonIntegrationTest`, annotate the test class or test methods with `@Transactional`.
- **Rationale**: Spring Test automatically rolls back transactions at the end of each test method, keeping the database in a clean state without needing hand-rolled `jdbcTemplate.update("DELETE FROM ...")` logic in `@BeforeEach`.
- **Alternative considered**: Manual table truncation in `@BeforeEach`. Rejected as brittle, high boilerplate, and prone to breaking whenever new entities or tables are added.

### Decision 2: Profile Hygiene & Redundancy Elimination
- **Approach**:
  - In `PersistenceRepositoryDataJpaTest`, use `@DataJpaTest` without the redundant `properties = "spring.jpa.hibernate.ddl-auto=validate"` since `application.yml` already defines it.
  - Rely on `application-test.yml` for test profile defaults.

### Decision 3: Idiomatic JPA Repositories Everywhere (Ban Raw SQL and `JdbcTemplate` from Tests)
- **Approach**:
  - Eliminate all `JdbcTemplate` dependencies and raw SQL execution from test sources across the entire project (`PersistenceRepositoryDataJpaTest`, `GravifonIntegrationTest`).
  - Use Spring Data JPA repositories, entity relationships, and domain repository ports for all database verification, setup, and cascade assertions.
  - Rely on normal JPA repository lifecycles and transactions instead of imperative `entityManager.flush()` workarounds or manual SQL queries.
- **Rationale**: Injecting `JdbcTemplate` to execute raw SQL in Spring Boot tests breaks the abstraction of testing via the ORM/repository layer, scatters brittle SQL string literals across tests, forces manual persistence context flushes, and circumvents the application's actual data access contracts. Tests should interact with persistence using the same repository abstractions as the production code.

### Decision 4: Mocking Discipline with MockitoExtension
- **Approach**:
  - Use `@ExtendWith(MockitoExtension.class)` with default strictness (`STRICT_STUBS`) on plain unit tests (`PlaylistServiceTest`, `PlaybackServiceTest`, `LibraryScanCoordinatorTest`, etc.).
  - Replace `PlaylistServiceTest.tracks(...)` with explicit, scenario-specific stubs (`when(trackRepository.findAllById(...)).thenReturn(...)`).
  - Eliminate mock-based repository filtering logic.
- **Rationale**: Faking repository mechanics in Mockito answers obscures what the service is actually orchestrating and leads to tests passing or failing due to fake repository bugs.

### Decision 5: Dedicated Aggregate Invariant Unit Tests
- **Approach**:
  - Create `PlaylistTest`: test `reorder`, `addEntries`, `removeEntries`, `rename`, `setMode`, and deduplication rules on the immutable `Playlist` record.
  - Create `PlaybackSessionTest`: test `selectPlaylist`, `selectTrack`, `nextTrack`, `reportPosition`, `setTransportState`, and `observeStream` on the `PlaybackSession` aggregate directly.
- **Rationale**: Fast, sub-millisecond execution that verifies domain rules directly without requiring Spring context or Mockito mocks.

## Risks / Trade-offs

- [Risk] `@Transactional` on `@SpringBootTest(webEnvironment = MOCK)` rolls back DB changes, but in-memory service singletons (`PlaybackService.session`) could retain state across methods.
  → Mitigation: Reset in-memory session or initialize fresh state in `@BeforeEach` via `PlaybackService` methods, separating memory state reset from database truncation.
