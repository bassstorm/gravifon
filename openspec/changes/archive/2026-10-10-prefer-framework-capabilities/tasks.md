# Tasks

## 1. Declarative configuration properties

- [x] 1.1 Rewrite `StreamRefreshProperties` as a plain record with `@Min(1)` on integers and `@NotNull` and `@DurationMin(nanos = 1)` on durations; delete the manual constructor and `@DefaultValue`. Verify `mvn clean test` compiles and passes.
- [x] 1.2 Apply the same pattern to `StreamProxyProperties` and `LibraryProperties`. Verify the application context still starts (`GravifonIntegrationTest` passes).
- [x] 1.3 In `application.yml`, switch durations to Boot's simple notation (`1h`, `1m`, `5s`, `15s`) while keeping the `${ENV_ALIAS:default}` placeholders and cron entry unchanged. Verify `grep -n "PT[0-9]" src/main/resources/application.yml` returns nothing and `GravifonIntegrationTest` passes.
- [x] 1.4 Delete `ScopedPropertiesTest` with no replacement. Verify `mvn clean test` passes and no remaining test references it.

## 2. Proxy client setup

- [x] 2.1 Rebuild the client in `HttpStreamProxyConfiguration` with `ClientHttpRequestFactoryBuilder.jdk()` and `ClientHttpRequestFactorySettings` using the proxy connect/read timeouts, and set `defaultCandidate = false` on the bean. Verify `HttpStreamProxyTest` passes.
- [x] 2.2 Use `HttpHeaders` constants instead of header-name literals in `HttpStreamProxy`. Verify `HttpStreamProxyTest` passes.
- [x] 2.3 Delete `HttpStreamProxyContextTest`. Verify `mvn clean test` passes and `HttpStreamProxyTest` still covers range forwarding and header copying.

## 3. Remove framework-verifying tests

- [x] 3.1 Delete `FlywayMigrationTest` and `NoOpTagWriteBackHandlerTest`; remove the Jackson serialization test from `ApiModelMappingTest` while keeping the `@AssertTrue` and mapping tests. Verify `mvn clean test` passes.
- [x] 3.2 Confirm `PersistenceRepositoryDataJpaTest` still proves schema validity via Flyway and `ddl-auto: validate`.

## 4. Conventions and documentation

- [x] 4.1 Add the framework-first rule, property-record conventions (no code defaults, defaults in `application.yml` with simple `Duration` notation, declarative constraints, `@DurationMin` for durations) and the no-framework-testing guidance to `AGENTS.md`, noting the domain ring stays framework-free. Verify the section renders and matches the delta spec scenarios.

## 5. Integration verification

- [x] 5.1 Run `mvn spotless:apply`, `mvn clean test`, `mvn clean verify`; confirm the JaCoCo bundle line coverage gate (80%) still passes.
- [x] 5.2 Run `openspec validate --all --no-interactive --json` and confirm no errors.
