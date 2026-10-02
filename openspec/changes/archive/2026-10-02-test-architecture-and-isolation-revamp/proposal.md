## Why

Following the H2 migration in Phase 1, our test architecture still relies on clunky workarounds: `GravifonIntegrationTest` manually executes imperative `DELETE FROM` SQL statements on every test, `application-test.yml` uses a fixed in-memory DB URL that risks state pollution without manual cleanup, `PersistenceRepositoryDataJpaTest` carries redundant property overrides, and unit tests like `PlaylistServiceTest` contain complex repository simulation in Mockito stubs instead of focused scenario stubs and dedicated model tests.

Revamping the test suite to use idiomatic Spring Boot test features (`@Transactional` test rollback, generative/isolated test profile datasources, strict MockitoExtension stubbing, and direct domain model invariant testing) simplifies the test suite, ensures reliable isolation, and removes maintenance friction.

## What Changes

- **Eliminate Raw SQL and `JdbcTemplate` Across the Test Suite**:
  - Remove all usages of `JdbcTemplate` and raw SQL strings across all test classes (`GravifonIntegrationTest`, `PersistenceRepositoryDataJpaTest`).
  - Rely exclusively on Spring Data JPA repositories and domain repository ports for test data setup, state assertions, cascade verification, and cleanup.
- **Declarative Test DB Isolation & Profiles**:
  - Update `src/test/resources/application-test.yml` to supply clean default in-memory database configuration.
  - Make `GravifonIntegrationTest` use declarative `@Transactional` rollback or isolated context isolation, eliminating manual database table truncation.
  - Remove redundant `spring.jpa.hibernate.ddl-auto=validate` property in `@DataJpaTest` on `PersistenceRepositoryDataJpaTest`.
- **Service Unit Test Cleanups & Mocking Discipline**:
  - Eliminate fake repository simulation in `PlaylistServiceTest.tracks(...)`; replace with concise, explicit scenario-level stubs.
  - Adopt `@ExtendWith(MockitoExtension.class)` with `Strictness.STRICT_STUBS` across service unit tests (`PlaylistServiceTest`, `PlaybackServiceTest`, `LibraryScanCoordinatorTest`, etc.).
  - Move cross-boundary persistence assertions (e.g. shared track updates across playlists) to persistence/integration test suites where real database semantics are verified.
- **Dedicated Domain Model Invariant Tests**:
  - Add focused unit tests for `Playlist` (reorder, rename, add, remove, deduplication, mode transitions) and `PlaybackSession` (state transitions, position bounds, transport actions) without Spring context or mocks.
- **Conventions in AGENTS.md**:
  - Document test boundary rules, MockitoExtension strict stubbing, test profile usage, declarative test isolation, and idiomatic repository-based assertions in `AGENTS.md`.

## Capabilities

### New Capabilities
<!-- None: test architecture and internal test suite revamp -->

### Modified Capabilities
<!-- None: skip_specs is enabled as user-facing system requirements are unchanged -->

## Impact

- **Test Code**: `GravifonIntegrationTest`, `PersistenceRepositoryDataJpaTest`, `PlaylistServiceTest`, and new domain unit tests (`PlaylistTest`, `PlaybackSessionTest`).
- **Configuration**: `application-test.yml`.
- **Documentation**: `AGENTS.md` testing conventions section.
- **Production Code**: Unchanged.
