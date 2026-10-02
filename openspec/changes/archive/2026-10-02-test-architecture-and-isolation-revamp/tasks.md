## 1. Project-Wide Test Isolation, Profile Standardization, and JdbcTemplate Removal

- [x] 1.1 In `GravifonIntegrationTest`, adopt declarative `@Transactional` test rollback, remove `JdbcTemplate` injection and all imperative `DELETE FROM` SQL table truncation in `@BeforeEach`, and verify tests pass cleanly with `mvn test -Dtest=GravifonIntegrationTest`
- [x] 1.2 In `PersistenceRepositoryDataJpaTest`, remove redundant `properties = "spring.jpa.hibernate.ddl-auto=validate"` attribute, remove `JdbcTemplate` injection, and replace all raw SQL child-row verification queries and manual `entityManager.flush()` calls with Spring Data JPA queries/methods, verifying with `mvn test -Dtest=PersistenceRepositoryDataJpaTest`
- [x] 1.3 Verify zero occurrences of `JdbcTemplate` remain in the test codebase and review `src/test/resources/application-test.yml` to ensure test defaults provide clean in-memory database settings without redundant property overrides

## 2. Unit Test Mocking and Service Boundary Cleanups

- [x] 2.1 In `PlaylistServiceTest`, eliminate fake repository simulation in `tracks(...)` helper and replace with explicit scenario-level stubs, verifying with `mvn test -Dtest=PlaylistServiceTest`
- [x] 2.2 In `PlaylistServiceTest`, update `sharedTrackReferenceIsVisibleInEveryPlaylistAfterRegistryUpdate` to verify genuine service behavior, and verify cross-boundary persistence in `PersistenceRepositoryDataJpaTest`
- [x] 2.3 Adopt `@ExtendWith(MockitoExtension.class)` with strict stubbing across service unit tests (`PlaylistServiceTest`, `PlaybackServiceTest`, `LibraryScanCoordinatorTest`) and verify all pass with `mvn test -Dtest=*ServiceTest,*CoordinatorTest`

## 3. Dedicated Domain Model Invariant Tests

- [x] 3.1 Create `PlaylistTest` under `com.gravifon.player.playlist.model` to test invariant operations (`reorder`, `addEntries`, `removeEntries`, `rename`, `setMode`, deduplication) in isolation, verifying with `mvn test -Dtest=PlaylistTest`
- [x] 3.2 Create `PlaybackSessionTest` under `com.gravifon.player.playback.model` to test session state transitions, position precedence, duration bounds, and transport states in isolation, verifying with `mvn test -Dtest=PlaybackSessionTest`
- [x] 3.3 Run domain model tests with `mvn test -Dtest=PlaylistTest,PlaybackSessionTest` and verify all pass

## 4. Documentation Conventions and Full Verification

- [x] 4.1 Update `AGENTS.md` with guidelines on declarative test database isolation (`@Transactional`), `MockitoExtension` strict stubbing, and avoiding fake repository simulation in unit tests
- [x] 4.2 Verify OpenSpec compliance with `openspec validate --all --no-interactive --json`
- [x] 4.3 Run full build and verification suite with `mvn clean verify` and confirm all tests pass and coverage gates are met
