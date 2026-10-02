## 1. Dependencies and Declarative Configuration

- [x] 1.1 In `pom.xml`, replace `org.xerial:sqlite-jdbc` and `org.hibernate.orm:hibernate-community-dialects` with `com.h2database:h2`, and verify with `mvn dependency:resolve`
- [x] 1.2 Delete `SqliteDataSourceConfiguration.java` and declare H2 datasource properties in `src/main/resources/application.yml` and `src/test/resources/application-test.yml`
- [x] 1.3 Update `src/main/resources/db/migration/V1__init.sql` with native H2 types (`BOOLEAN`, `BIGINT`), non-reserved column names (`metadata_key`, `metadata_value`), and composite index on `(kind, expires_after)`, and verify syntax

## 2. Persistence Layer Test Suite Revamp

- [x] 2.1 Revamp `PersistenceRepositoryDataJpaTest` into an in-memory `@DataJpaTest` without SQLite `@Import` or `@AutoConfigureTestDatabase(replace=NONE)` workarounds, verifying Spring Data JPA entity mappings with Flyway schema (`ddl-auto: validate`) and testing full CRUD, metadata ordering, and cascade semantics for track, playlist, and playback state adapters
- [x] 2.2 Revamp `FlywayMigrationTest` to verify that `V1__init.sql` applies cleanly and idempotently on H2
- [x] 2.3 Run persistence unit tests with `mvn test -Dtest=PersistenceRepositoryDataJpaTest,FlywayMigrationTest` and verify all pass

## 3. Integration Tests and Clean Fixture State

- [x] 3.1 Update `GravifonIntegrationTest` to use clean, isolated test data setup without conditional reuse of existing database playlists
- [x] 3.2 Update `PlaybackRestartIntegrationTest` to verify application restart and state persistence using an isolated file-backed H2 configuration directory
- [x] 3.3 Run all integration tests with `mvn test -Dtest=*IntegrationTest` and verify green runs

## 4. Documentation, Spec Delta and Full Suite Verification

- [x] 4.1 Update `AGENTS.md` with explicit Spring Boot database and declarative configuration conventions
- [x] 4.2 Verify OpenSpec compliance with `openspec validate --all --no-interactive --json`
- [x] 4.3 Run full verification suite with `mvn clean verify` and confirm all 100+ tests and JaCoCo coverage thresholds pass
