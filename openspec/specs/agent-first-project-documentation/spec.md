# Agent-First Project Documentation Specification

## Purpose
Define the repository guidance and contribution conventions that keep agent-assisted development consistent, testable, and aligned with the project's architecture.

## Requirements

### Requirement: Repository includes agent-first contribution guidance
The system SHALL document architecture, coding/testing principles, and agent workflow expectations for contributors.

#### Scenario: New contributor onboarding
- **WHEN** an agent or developer reads project guidance docs
- **THEN** they understand architecture boundaries and implementation/testing standards

### Requirement: OpenSpec configuration and docs stay aligned
The system MUST keep OpenSpec config and repository guidance consistent with planning and implementation workflows.

#### Scenario: Planning workflow execution
- **WHEN** an agent runs OpenSpec propose/apply/archive flows
- **THEN** configuration and docs provide consistent instructions and paths

### Requirement: Operational behavior is documented
The system SHALL document key operational constraints, including in-memory state lifecycle and supported media/playlist formats.

#### Scenario: Product expectation setting
- **WHEN** a stakeholder reviews operational docs
- **THEN** they can identify current supported behavior and deferred capabilities

#### Scenario: Access scope is reviewed
- **WHEN** a stakeholder reviews access constraints
- **THEN** documentation explicitly states single-user scope, no authn/authz, and no session management

### Requirement: Testing and implementation conventions are documented
The system MUST document project conventions for high unit-test coverage, preferred testing libraries, lean Java coding style, and clean verification flows.

#### Scenario: Contributor follows testing policy
- **WHEN** a contributor implements or changes product behavior
- **THEN** tests target more than 80 percent coverage of business functionality using AssertJ, Mockito, and Spring/Spring Boot testing capabilities

#### Scenario: Contributor follows clean build policy
- **WHEN** a contributor verifies changes before merge
- **THEN** they use clean-state commands such as `mvn clean ...` and `docker compose down -v` where applicable

### Requirement: Logging and correlation conventions are documented
The system MUST document log-level semantics and correlation-id lifecycle scope so troubleshooting behavior is implemented consistently.

#### Scenario: Contributor implements diagnostics
- **WHEN** a contributor adds client or server logs
- **THEN** they follow the shared severity convention (`trace`, `debug`, `info`, `warn`, `error`) and playback-lifecycle correlation-id scope
