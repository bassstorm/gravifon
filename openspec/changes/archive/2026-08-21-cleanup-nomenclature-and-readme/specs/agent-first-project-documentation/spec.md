## MODIFIED Requirements

### Requirement: MVP operational behavior is documented
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
