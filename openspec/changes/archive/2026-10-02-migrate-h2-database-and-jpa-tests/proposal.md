## Why

Gravifon currently uses an imperative `SqliteDataSourceConfiguration` with custom SQLite native JDBC and community Hibernate dialects. This introduces unnecessary boilerplate, complex lifecycle workarounds in `@Bean` creation, and test-suite friction with temporary file paths and state leakage. Migrating to an embedded H2 database (file-backed at runtime, in-memory for tests) enables clean, declarative Spring Boot auto-configuration, leverages idiomatic Spring Data JPA entity mappings validated at startup, eliminates cross-test state leakage, and revamps database and integration test suites.

## What Changes

- Replace `sqlite-jdbc` and `hibernate-community-dialects` dependencies with `com.h2database:h2`.
- Remove `SqliteDataSourceConfiguration.java` and configure the database declaratively via `spring.datasource.*` in `application.yml` and `application-test.yml`.
- Standardize migration `V1__init.sql` using standard SQL/H2 data types (`BOOLEAN`, `BIGINT`), non-reserved column names (`metadata_key`, `metadata_value`), and a composite index on `(kind, expires_after)`.
- Embrace idiomatic Spring Data JPA: configure `spring.jpa.hibernate.ddl-auto: validate` and maintain clear separation between domain repository ports and Spring Data `JpaRepository` stores.
- Revamp `PersistenceRepositoryDataJpaTest` into an idiomatic `@DataJpaTest` that uses an in-memory database and tests full CRUD, ordering, and cascade rules across all JPA repository adapters.
- Revamp `FlywayMigrationTest` to validate migration application and repeatability against H2.
- Fix state leakage in `GravifonIntegrationTest` and `PlaybackRestartIntegrationTest` so tests execute against clean, deterministic database state.
- Update `containerized-runtime` specification requirements to describe embedded database persistence under `/config` rather than SQLite-specific implementation details.
- Record core database, Spring Data JPA, and Spring Boot conventions in `AGENTS.md`.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `containerized-runtime`: Update volume persistence requirement from SQLite-specific storage to generic embedded database state under `/config`.

## Impact

- **Dependencies**: Remove `org.xerial:sqlite-jdbc` and `org.hibernate.orm:hibernate-community-dialects`; add `com.h2database:h2`.
- **Configuration**: `application.yml` and `application-test.yml` declare `spring.datasource.*`; `SqliteDataSourceConfiguration.java` is removed.
- **Database Schema**: `V1__init.sql` updated with standard types.
- **Testing**: In-memory database used across slice and integration tests; zero host `/tmp` leaks or state collisions between tests.
- **Runtime**: `/config/gravifon.mv.db` is stored under `/config` volume.
