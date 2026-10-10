# Spec Delta

## ADDED Requirements

### Requirement: Container health check reflects service health
The committed Compose file SHALL define a container health check that polls the service health endpoint, and runtime documentation MUST cover the health, liveness, and readiness endpoints.

#### Scenario: Compose service reports container health
- **WHEN** the service runs through the committed `docker-compose.yaml`
- **THEN** the container health status is derived from the service health endpoint

#### Scenario: Health status reflects failing components
- **WHEN** a health component such as the database or music library path is not UP
- **THEN** the container is reported unhealthy

#### Scenario: Operator learns operational endpoints
- **WHEN** a new operator follows runtime documentation
- **THEN** they can identify the health, liveness, and readiness endpoints and their meaning
