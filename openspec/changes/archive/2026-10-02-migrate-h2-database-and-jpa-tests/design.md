## Context

Gravifon is a local-network audio streaming service running on Spring Boot 3.5.16 and Java 21. See `proposal.md` for motivation.
Currently, SQLite is configured via `SqliteDataSourceConfiguration`, using `org.xerial:sqlite-jdbc` and `org.hibernate.orm:hibernate-community-dialects`.
The goal of this design is to transition the persistence engine to embedded H2, use standard Spring Boot declarative properties, modernize `V1__init.sql`, and revamp unit and integration test fixtures to prevent state leakage and artificial stubbing.

## Goals / Non-Goals

**Goals:**
- Replace SQLite with H2 across application runtime and tests.
- Remove custom `SqliteDataSourceConfiguration` in favor of declarative `spring.datasource.*` in `application.yml` and `application-test.yml`.
- Standardize `V1__init.sql` schema with standard SQL types (`BOOLEAN`, `BIGINT`), non-reserved column names (`metadata_key`, `metadata_value`), and composite index on `(kind, expires_after)`.
- Maximize Spring Data JPA capabilities: configure `spring.jpa.hibernate.ddl-auto: validate` to guarantee entity-to-schema alignment, maintaining clear separation between domain repository ports and Spring Data `JpaRepository` stores.
- Revamp `PersistenceRepositoryDataJpaTest` into an in-memory `@DataJpaTest` covering all repository adapters.
- Revamp `FlywayMigrationTest` to verify migration repeatability against H2.
- Clean up `GravifonIntegrationTest` and `PlaybackRestartIntegrationTest` to be fully deterministic and hermetic.
- Document database and Spring Data JPA conventions in `AGENTS.md`.

**Non-Goals:**
- Backward compatibility or schema migration for old SQLite files (there is no production database to preserve).
- Moving `@Transactional` annotations from controllers (deferred to Phase 3).
- Adding Bean Validation to DTOs (deferred to Phase 3).
- Modifying audio streaming or `HttpClient` code (deferred to Phase 4).

## Decisions

### Decision 1: Use H2 for both runtime file persistence and test in-memory storage
- **Rationale**: Keeps runtime and testing on the identical database engine, eliminating engine divergence and the dual-database anti-pattern. Spring Boot provides first-class, battle-tested auto-configuration and dialect support for H2.
- **Alternatives considered**:
  - *Keep SQLite for runtime and use H2 for tests*: Introduces dialect divergence and breaks SQLite-specific migrations.
  - *Keep SQLite for both runtime and tests*: Retains native C JNI dependency, community dialect quirks, and complex temporary file lifecycle in tests.

### Decision 2: Declarative configuration via Spring Boot Auto-Configuration
- **Configuration**:
  - `application.yml`:
    ```yaml
    spring:
      datasource:
        url: jdbc:h2:file:${GRAVIFON_CONFIG_DIR:/config}/gravifon;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
        driver-class-name: org.h2.Driver
        username: sa
        password: ""
      jpa:
        open-in-view: false
        hibernate:
          ddl-auto: validate
    ```
  - `application-test.yml`:
    ```yaml
    spring:
      datasource:
        url: jdbc:h2:mem:gravifontest;DB_CLOSE_DELAY=-1
    ```
- **Rationale**: Removes imperative DataSource bean setup and aligns with Spring Boot best practices.

### Decision 3: Standardize Schema and Indexing in `V1__init.sql`
- `failing INTEGER NOT NULL DEFAULT 0` $\rightarrow$ `failing BOOLEAN NOT NULL DEFAULT FALSE`
- `duration_s INTEGER` $\rightarrow$ `duration_s BIGINT`
- Timestamps (`created_at`, `updated_at`, `error_at`, `expires_after`) mapped as `BIGINT`.
- Avoid reserved keywords: column names in `track_metadata` use `metadata_key` and `metadata_value` instead of `key` and `value`, mapped cleanly to `@Column(name = "metadata_key")` and `@Column(name = "metadata_value")` in `TrackMetadataEntity`.
- Indexing: H2 does not support `WHERE` clause partial indexes; use standard composite index `CREATE INDEX idx_track_stream_expires_after ON track(kind, expires_after);`, which directly covers stream expiry lookup queries.

### Decision 4: Leverage Idiomatic Spring Data JPA Architecture
- **Boundary Separation**: Application and domain services consume domain repository interfaces (`TrackRepository`, `PlaylistRepository`, `PlaybackStateRepository`) returning immutable domain models.
- **Persistence Adapters**: Persistence layer implements those ports via `@Repository` adapters (`JpaTrackRepository`, `JpaPlaylistRepository`, `JpaPlaybackStateRepository`) delegating to Spring Data `JpaRepository` stores (`JpaTrackStore`, `JpaPlaylistStore`, `JpaPlaybackStateStore`).
- **Entity Integrity**: Flyway owns schema definition; Hibernate validates mappings on boot via `ddl-auto: validate`.

### Decision 5: Revamp Persistence & Integration Test Suites
- `PersistenceRepositoryDataJpaTest`: Uses `@DataJpaTest`, drops `@AutoConfigureTestDatabase(replace = NONE)` and `@Import(SqliteDataSourceConfiguration.class)`. Tests track, playlist, and playback state adapters against clean in-memory H2.
- `GravifonIntegrationTest`: Ensures fixtures establish an explicit known baseline rather than conditionally branching on existing database state.

## Risks / Trade-offs

- [Risk] H2 `.mv.db` file format is not readable by standard SQLite desktop viewers.
  → Mitigation: Project is a self-contained local service; external database viewers without JVM are not a requirement. Spring Boot H2 Console can be enabled if ad-hoc inspection is needed.
- [Risk] Existing containers mounting `/config` might have leftover `gravifon.db`.
  → Mitigation: Fresh deployments will create `gravifon.mv.db`; old `gravifon.db` can safely be deleted or ignored.
