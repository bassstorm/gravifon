# Proposal

## Why

The code base re-implements capabilities Spring Boot, Spring Framework and Hibernate Validator already provide, and carries tests that verify the framework instead of Gravifon behavior. `StreamRefreshProperties` hand-rolls positive-value checks next to `@Min`, `ScopedPropertiesTest` builds a `Binder` without a validator (so the declared constraints are never exercised), and `HttpStreamProxyContextTest` only asserts that wiring works. The 80% coverage gate is bundle-wide and scoped to business logic, so it does not justify these tests. The project principle "reuse framework capability" is not written down in a place agents apply it, so the pattern keeps recurring.

## What Changes

- Record a framework-first convention in the testing and implementation conventions: check Boot/Spring/Hibernate Validator before writing validation, configuration binding, HTTP client, error mapping, range handling or scheduling code; do not test what the framework guarantees; keep the domain ring framework-free.
- Rewrite `StreamRefreshProperties`, `StreamProxyProperties` and `LibraryProperties` as plain records with declarative constraints (`@Min(1)` for integers, `@NotNull` and Hibernate Validator `@DurationMin` for durations); delete the hand-written canonical constructors, `@DefaultValue` annotations and `IllegalArgumentException` checks.
- Replace the hand-built JDK `HttpClient` in `HttpStreamProxyConfiguration` with Boot's `ClientHttpRequestFactoryBuilder`/`HttpClientSettings`, mark the proxy `RestClient` bean `defaultCandidate = false`, and use `HttpHeaders` constants instead of header-name literals.
- Make `application.yml` the single source of truth for defaults (no defaults in code) and use Spring Boot's simple `Duration` notation (`1h`, `1m`, `5s`) instead of ISO-8601 (`PT1H`, `PT1M`, `PT5S`). Existing environment aliases are kept.
- Delete tests that verify framework behavior or trivial accessors, with no replacement: `ScopedPropertiesTest`, `HttpStreamProxyContextTest`, `FlywayMigrationTest`, `NoOpTagWriteBackHandlerTest`, and the Jackson check in `ApiModelMappingTest`. Configuration properties and bean wiring are not unit-tested.
- Update `AGENTS.md` with the framework-first rule and the property-record conventions.
- Non-goals (follow-up spikes, not part of this change): replacing `ApiExceptionHandler` with `ResponseEntityExceptionHandler`/`ProblemDetail` (touches the `player-rest-api` error contract and its catch-all handler does not log), and replacing manual file streaming in `AudioStreamingService` with `ResponseEntity<Resource>` range support (needs a way to keep `observeStream`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-first-project-documentation`: the "Testing and implementation conventions are documented" requirement gains framework-first and no-framework-testing conventions, and clarifies that the coverage target applies to business functionality rather than configuration or wiring glue.

## Impact

- Code: `config/StreamRefreshProperties`, `config/StreamProxyProperties`, `config/LibraryProperties`, `streaming/HttpStreamProxyConfiguration`, `streaming/HttpStreamProxy`, `src/main/resources/application.yml`.
- Tests: delete `ScopedPropertiesTest`, `HttpStreamProxyContextTest`, `FlywayMigrationTest`, `NoOpTagWriteBackHandlerTest`; trim `ApiModelMappingTest`. No new tests.
- Docs: `AGENTS.md`.
- Behavior: invalid stream/proxy configuration now fails startup with Boot's `BindValidationException` message instead of a wrapped `IllegalArgumentException`. A removed key in `application.yml` now fails startup instead of silently using a code default. ISO-8601 values supplied through existing environment variables remain valid, because Boot accepts both notations.
- Verification: JaCoCo bundle line coverage must stay at or above 80% (currently about 88%).
