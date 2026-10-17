## MODIFIED Requirements

### Requirement: Repository includes agent-first contribution guidance
The system SHALL document architecture, coding/testing principles, and agent workflow expectations for contributors, tailored to human readers in `README.md` and agent/developer implementors in `AGENTS.md`.

#### Scenario: New contributor onboarding
- **WHEN** an agent or developer reads project guidance docs
- **THEN** they understand the vertical package slices (`session`, `registry`, `playlist`, `playback`, `streaming`), their one-way dependency rules, cross-slice event/provider patterns, and implementation/testing standards
- **AND** `README.md` presents a high-level architectural overview while `AGENTS.md` defines concrete package, persistence, and verification rules

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

#### Scenario: Framework-first rule respects architecture boundaries
- **WHEN** a contributor works in domain model packages (`..model..`, `..identity..`)
- **THEN** the convention states that domain models remain framework-free and are expressed in plain Java code verified by ArchUnit without third-party architectural marker annotations
