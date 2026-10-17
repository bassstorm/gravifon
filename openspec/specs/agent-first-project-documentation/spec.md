# Agent-First Project Documentation Specification

## Purpose
Define the repository guidance and contribution conventions that keep agent-assisted development consistent, testable, and aligned with the project's architecture.

## Requirements

### Requirement: Repository includes agent-first contribution guidance
The system SHALL document architecture, coding/testing principles, and agent workflow expectations for contributors, tailored to human readers in `README.md` and agent/developer implementors in `AGENTS.md`.

#### Scenario: New contributor onboarding
- **WHEN** an agent or developer reads project guidance docs
- **THEN** they understand the vertical package slices (`session`, `registry`, `playlist`, `playback`, `streaming`), their one-way dependency rules, cross-slice event/provider patterns, and implementation/testing standards
- **AND** `README.md` presents a high-level architectural overview with Mermaid diagrams while `AGENTS.md` defines concrete package, repository/converter, persistence, and verification rules

### Requirement: OpenSpec configuration and docs stay aligned
The system MUST keep OpenSpec config and repository guidance consistent with planning and implementation workflows, keeping `openspec/config.yaml` scoped to OpenSpec artifact and diagram formatting rules (such as requiring Mermaid diagrams instead of ASCII art) while project engineering principles live in specifications and their `README.md`/`AGENTS.md` projections.

#### Scenario: Planning workflow execution
- **WHEN** an agent runs OpenSpec propose/apply/archive flows
- **THEN** configuration and docs provide consistent instructions and paths

#### Scenario: OpenSpec config enforces Mermaid diagrams while specs own engineering rules
- **WHEN** a contributor or agent generates OpenSpec artifacts (`design.md`) or updates repository documentation (`README.md`, `AGENTS.md`)
- **THEN** `openspec/config.yaml` directs them to use Mermaid fenced code blocks (` ```mermaid `) rather than ASCII art diagrams, without duplicating project engineering principles owned by specifications and `AGENTS.md`

### Requirement: Operational behavior is documented
The project documentation SHALL describe correlation propagation, unverified username session acquisition, instance-session lifetimes, shared-registry stream registration, and diagnostic verification for multi-instance clients.

#### Scenario: Agent investigates session behavior
- **WHEN** an agent inspects project docs
- **THEN** it can identify how `X-Client-Session`, `CorrelationId`, username resolution, and stream registration interact without reading source code first

### Requirement: Testing and implementation conventions are documented
The system MUST document project conventions for high unit-test coverage, preferred testing libraries, lean Java coding style, and clean verification flows.

#### Scenario: Contributor follows testing policy
- **WHEN** a contributor implements or changes product behavior
- **THEN** tests target more than 80 percent coverage of business functionality using AssertJ, Mockito, and Spring/Spring Boot testing capabilities
- **AND** the convention states that the coverage target applies to business behavior, not to configuration, bean wiring, or behavior the framework already guarantees

#### Scenario: Contributor follows clean build policy
- **WHEN** a contributor verifies changes before merge
- **THEN** they use clean-state commands such as `mvn clean ...` and `docker compose down -v` where applicable

### Requirement: Framework capabilities are preferred over bespoke code
The system MUST document that contributors reuse capabilities already provided by the application framework and its libraries before writing custom code, avoid redundant 1:1 pass-through interfaces under YAGNI, and do not re-verify framework guarantees in tests.

#### Scenario: Contributor adds validation, configuration binding, or client setup
- **WHEN** a contributor needs validation, configuration binding, defaults, HTTP client setup, error mapping, cross-slice events, or scheduling
- **THEN** the documented convention directs them to use the declarative framework capability first
- **AND** custom code or an extra interface layer is justified only when multiple implementations exist or no framework capability meets the requirement

#### Scenario: Contributor writes tests for declarative configuration
- **WHEN** a contributor adds or changes configuration properties or bean wiring
- **THEN** the convention directs them to keep defaults in the externalized application configuration and to use declarative constraints
- **AND** unit tests that only assert accessors, bean existence, property binding, or framework behavior are not required

#### Scenario: Contributor writes persistence queries or externalized configuration
- **WHEN** a contributor adds repository store queries or `gravifon.*` configuration properties
- **THEN** `Jpa*Store` interfaces prefer Spring Data derived query methods (including `@Lock` derived queries) over unnecessary `@Query` strings, reserving `@Query` for scalar projections
- **AND** configuration properties use singular slice prefixes aligned with Spring Boot relaxed environment-variable binding so `application.yml` contains literal defaults without `${GRAVIFON_...:default}` placeholder aliases

#### Scenario: Framework-first rule respects architecture boundaries
- **WHEN** a contributor works in domain model packages (`..model..`, `..identity..`)
- **THEN** the convention states that domain models remain framework-free and are expressed in plain Java code verified by ArchUnit without third-party architectural marker annotations

### Requirement: Logging and correlation conventions are documented
The system MUST document log-level semantics and correlation-id lifecycle scope so troubleshooting behavior is implemented consistently.

#### Scenario: Contributor implements diagnostics
- **WHEN** a contributor adds client or server logs
- **THEN** they follow the shared severity convention (`trace`, `debug`, `info`, `warn`, `error`) and playback-lifecycle correlation-id scope

### Requirement: Domain models use rich types and convert to strings only at boundaries
Repository guidance SHALL require domain models, value objects, and internal services to use rich domain or JDK types (`Path`, `URI`, `TrackMetadata`, `Username`, and specific track subtypes), converting to and from raw `String` or `Map` representations only at HTTP DTO (`api/model`) and persistence converter (`*Converter`) boundaries.

#### Scenario: Contributor models paths, URLs, metadata, or user names
- **WHEN** a contributor defines or updates domain models and service contracts
- **THEN** filesystem paths use `Path`, stream/source URLs use `URI`, metadata uses `TrackMetadata`, and user names use `Username`
- **AND** raw `String` or `Map` conversion occurs only in API DTO mapping and package-private repository `*Converter` classes

#### Scenario: Unified source normalization
- **WHEN** track sources are normalized for identity derivation or persistence
- **THEN** a single stateless `SourceNormalizer` in `registry/identity` normalizes both `Path` and `URI` values without ad-hoc character replacements in callers or 1:1 normalizer interfaces

### Requirement: Validation is enforced once at external and type-construction boundaries
Repository guidance SHALL require two-boundary validation: declarative Jakarta Bean Validation (`@Valid`, `@Validated`) at HTTP/configuration boundaries and invariant checks in domain/command record compact constructors, without repeating defensive null/blank/element checks across internal services and repositories.

#### Scenario: Contributor adds validation for an operation
- **WHEN** a contributor validates request payloads, configuration properties, or domain/command objects
- **THEN** external inputs are validated declaratively via `@Valid`/`@Validated` and domain invariants are enforced in record compact constructors
- **AND** internal application services, persistence helpers, and repositories trust validated domain types instead of repeating null or blank checks
