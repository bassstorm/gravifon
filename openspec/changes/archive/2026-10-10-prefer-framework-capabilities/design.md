# Design

## Context

Findings come from a read-only walk-through of the code base (see proposal.md - Why). Relevant constraints:

- Spring Boot 3.5.16, Spring Framework 6.2.19, Hibernate Validator 8.0.3, Java 21.
- JaCoCo gate is a bundle-wide 80% line ratio (about 88% today); config records contribute roughly 30 lines.
- The domain ring is framework-free (jMolecules onion + ArchUnit); the application and infrastructure rings may use framework features.
- AGENTS.md already mandates defensive validation in application services and a non-regex model for conditional track-failure reports, so those stay.

## Goals / Non-Goals

**Goals:**
- Make the framework-first rule discoverable in AGENTS.md and the documentation spec.
- Remove the identified bespoke validation, hardcoded defaults, client setup, and framework-verifying tests.

**Non-Goals:**
- Replacing `ApiExceptionHandler` with `ResponseEntityExceptionHandler`/`ProblemDetail` (error contract change; separate spike).
- Replacing manual file streaming in `AudioStreamingService` with `ResponseEntity<Resource>` range support (needs a seam for `observeStream`; separate spike).
- Touching domain-ring invariants, application-service defensive validation, `MusicLibraryHealthIndicator` timeout handling, or selector/resolver registries.

## Decisions

1. **Properties are plain records with no code defaults.** Spring Boot binds records through the canonical constructor, so the explicit constructors and `@DefaultValue` annotations are removed. Alternative: keep `@DefaultValue` in code; rejected because defaults hardcoded in annotations are harder to externalize and duplicate what `application.yml` already declares.

2. **Constraints: `@Min(1)` for integers and `@NotNull` plus Hibernate Validator `@DurationMin(nanos = 1)` for durations.** Bean Validation `@Positive` does not support `java.time.Duration`; `@DurationMin` is the standard equivalent. These also guard against an incomplete `application.yml`: a missing key binds to `0` or `null` and fails startup with Boot's binding error. Alternative: custom `@PositiveDuration` annotation; rejected as another bespoke artifact.

3. **Configuration properties are not unit-tested.** With defaults in yml and constraints declarative, any property test would only verify Boot binding, Hibernate Validator, or the yml file. `ScopedPropertiesTest` is deleted without replacement. Existing integration tests that start the application context already fail if the yml is incomplete.

4. **Proxy `RestClient` is built from Boot's `ClientHttpRequestFactoryBuilder.jdk()` and `HttpClientSettings` carrying the connect/read timeouts.** The bean is marked `defaultCandidate = false` so it is only injected by qualifier, replacing the single-bean assertion in the context test. Alternative: remove the bean and build the client in the `HttpStreamProxy` constructor from `RestClient.Builder`; rejected to keep client configuration separate from proxy logic. `HttpStreamProxyContextTest` is deleted; `HttpStreamProxyTest` covers behavior.

5. **`application.yml` is the single source of defaults, written in Boot's simple notation.** Defaults stay in the existing `${ENV_ALIAS:default}` placeholders so they remain externalizable, and durations use `1h`, `1m`, `5s`, `15s` instead of `PT1H`, `PT1M`, `PT5S`, `PT15S`. Boot parses both forms, so ISO-8601 values in existing environment variables keep working. The environment aliases are unchanged. Alternative: duplicate defaults in code; rejected as two sources of truth.

6. **Framework-testing tests are removed, not rewritten:** `ScopedPropertiesTest`, `HttpStreamProxyContextTest`, `FlywayMigrationTest` (schema validity is already proven by `@DataJpaTest` with Flyway and `ddl-auto: validate`, and it uses raw SQL contrary to AGENTS.md), `NoOpTagWriteBackHandlerTest`, and the Jackson serialization test in `ApiModelMappingTest`. The `@AssertTrue` validation assertions in `ApiModelMappingTest` stay because they test project-owned logic.

7. **Convention wording lives in AGENTS.md (rule and checklist) and the documentation spec (behavioral scenarios).** AGENTS.md carries the actionable rule; the spec records that the convention exists.

## Risks / Trade-offs

- [Removing tests lowers coverage] -> Current bundle coverage is about 88%; run `mvn clean verify` and confirm the gate before and after.
- [An incomplete `application.yml` is now caught only at startup] -> Constraints fail startup with a clear binding error, and the integration tests that boot the context exercise it.
- [`@DurationMin` is Hibernate Validator specific, not Jakarta Bean Validation] -> Hibernate Validator is the validation provider shipped by `spring-boot-starter-validation`; acceptable in the infrastructure ring.

## Migration Plan

No data or deployment migration. Implement with the verification workflow in AGENTS.md (`mvn spotless:apply`, `mvn clean test`, `mvn clean verify`); roll back by reverting the change.
