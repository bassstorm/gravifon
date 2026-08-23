## Why

The development container and the documented verification workflow currently require contributors to move between environments: Maven and OpenSpec run in the Dev Container, while Docker-sensitive checks run on the host. Enabling Docker-in-Docker gives the Dev Container an isolated Docker daemon so the project can use one consistent environment for build, Compose, and container verification.

The feature configuration is already present in the working tree, so this change captures that configuration as an intentional repository capability and brings contributor documentation into alignment.

## What Changes

- Keep the Docker-in-Docker feature enabled in `.devcontainer/devcontainer.json` and its lockfile.
- Treat Docker CLI, Docker Compose, and a usable Docker daemon as supported Dev Container capabilities.
- Update contributor guidance so container-sensitive verification can be performed inside the Dev Container.
- Remove or revise guidance that says the Dev Container lacks Docker CLI access or requires host mode for Docker workflows.
- Document the isolation and lifecycle implications of Docker-in-Docker, including rebuilding the Dev Container after feature changes and the scope of containers, images, and volumes created inside it.
- Preserve the existing Gravifon application runtime and Compose contract; this change does not alter application APIs, image behavior, or deployment configuration.

## Capabilities

### New Capabilities

None. This is a development-tooling and documentation change, not a change to product behavior.

### Modified Capabilities

None. The existing application runtime requirements remain unchanged.

## Impact

- `.devcontainer/devcontainer.json` and `.devcontainer/devcontainer-lock.json` become part of the supported verification environment.
- `AGENTS.md` and `README.md` need updates to describe the unified workflow accurately.
- Contributors can run Docker image builds, Compose checks, and container-sensitive verification from the Dev Container.
- Docker state is owned by the Dev Container's nested daemon rather than the host daemon, so it has separate images, containers, networks, and volumes.
- No application source code, REST API, persistence model, or production container runtime behavior is affected.