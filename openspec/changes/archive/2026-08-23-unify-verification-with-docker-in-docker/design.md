## Context

The Dev Container already provides Java 21, Maven, Node 22, OpenSpec, and ripgrep. The Docker-in-Docker feature adds a private Docker daemon and Docker tooling to that environment. The existing project guidance separates ordinary verification from container-sensitive verification by assigning them to different environments.

## Goals / Non-Goals

**Goals:**

- Make the Dev Container the default environment for the complete verification workflow.
- Keep Docker state isolated from the host Docker daemon.
- Make the feature configuration, lockfile, and contributor instructions describe the same supported workflow.
- Preserve clean-state checks for both Maven and Docker Compose.

**Non-Goals:**

- Change the Gravifon application image, Compose runtime, ports, mounts, or environment variables.
- Require access to the host Docker socket.
- Make Docker-in-Docker a production deployment requirement.
- Add application-level tests for the development-container configuration.

## Decisions

### Use Docker-in-Docker instead of the host socket

The Dev Container uses the Docker-in-Docker feature's private daemon. This makes the verification environment self-contained and avoids coupling the repository to a host socket path, host daemon configuration, or host user permissions. Mounting the host socket was considered, but would weaken isolation and make the workflow less portable across development hosts.

### Treat the existing feature and lockfile as intentional change scope

The configuration and resolved lock entry are already present in the working tree. They remain part of this change so the environment capability is reviewed and documented together with its workflow implications rather than appearing as an undocumented prerequisite.

### Make contributor documentation the source of workflow truth

Update `AGENTS.md` for agent and contributor operating guidance and `README.md` for the project-facing setup path. The existing application runtime specification remains unchanged because it describes the deployed service, not the development environment.

### Validate from inside the rebuilt Dev Container

Verification should include Docker client/server availability, Compose configuration, Maven verification, and a clean Compose lifecycle. Rebuilding or recreating the Dev Container is required after changing Dev Container features so the documented environment is actually exercised.

## Risks / Trade-offs

- [Nested Docker requires additional privileges and resources] → Document the requirement and validate the feature during Dev Container rebuild; retain host-mode fallback only as an operational escape hatch, not as the normal project workflow.
- [Docker state is separate from the host and may be discarded with the Dev Container] → Document that images, containers, networks, and volumes belong to the nested daemon and should not be treated as host-persistent state.
- [The Docker-in-Docker feature may change independently when configured as `latest`] → Keep the generated lockfile committed and refresh it deliberately when updating the feature.
- [Running Compose inside the Dev Container can make host access less obvious] → Preserve the existing port and volume documentation and state that application access still uses the published service URL and configured mounts.