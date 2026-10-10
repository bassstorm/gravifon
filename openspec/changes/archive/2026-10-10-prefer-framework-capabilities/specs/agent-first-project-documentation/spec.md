## MODIFIED Requirements

### Requirement: Testing and implementation conventions are documented
The system MUST document project conventions for high unit-test coverage, preferred testing libraries, lean Java coding style, and clean verification flows.

#### Scenario: Contributor follows testing policy
- **WHEN** a contributor implements or changes product behavior
- **THEN** tests target more than 80 percent coverage of business functionality using AssertJ, Mockito, and Spring/Spring Boot testing capabilities
- **AND** the convention states that the coverage target applies to business behavior, not to configuration, bean wiring, or behavior the framework already guarantees

#### Scenario: Contributor follows clean build policy
- **WHEN** a contributor verifies changes before merge
- **THEN** they use clean-state commands such as `mvn clean ...` and `docker compose down -v` where applicable

## ADDED Requirements

### Requirement: Framework capabilities are preferred over bespoke code
The system MUST document that contributors reuse capabilities already provided by the application framework and its libraries before writing custom code, and that tests do not re-verify framework guarantees.

#### Scenario: Contributor adds validation, configuration binding, or client setup
- **WHEN** a contributor needs validation, configuration binding, defaults, HTTP client setup, error mapping, or scheduling
- **THEN** the documented convention directs them to use the declarative framework capability first
- **AND** custom code is justified only when no framework capability meets the requirement

#### Scenario: Contributor writes tests for declarative configuration
- **WHEN** a contributor adds or changes configuration properties or bean wiring
- **THEN** the convention directs them to keep defaults in the externalized application configuration and to use declarative constraints
- **AND** unit tests that only assert accessors, bean existence, property binding, or framework behavior are not required

#### Scenario: Framework-first rule respects architecture boundaries
- **WHEN** a contributor works in the domain ring
- **THEN** the convention states that domain invariants remain framework-free and are expressed in plain domain code
