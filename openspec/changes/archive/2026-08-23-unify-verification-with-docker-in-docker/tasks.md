## 1. Development Container Configuration

- [x] 1.1 Review and retain the Docker-in-Docker feature declaration in `.devcontainer/devcontainer.json`, with the resolved feature entry in `.devcontainer/devcontainer-lock.json`; verify the configuration parses and the lock entry matches the selected feature.
- [x] 1.2 Rebuild or recreate the Dev Container and verify `docker version` reports both a usable client and server from inside the container.

## 2. Contributor Documentation

- [x] 2.1 Update `AGENTS.md` to describe Docker CLI, Docker Compose, and the nested Docker daemon as available in the Dev Container; verify no guidance still claims Docker is unavailable there.
- [x] 2.2 Update `README.md` with the unified verification workflow, Dev Container rebuild requirement, Docker-in-Docker isolation, and the distinction between development-container checks and application runtime deployment; verify the documented commands match repository scripts and Compose configuration.

## 3. Verification Workflow

- [x] 3.1 Run `mvn clean test` and `mvn clean verify` from the Dev Container; verify both commands pass with the documented environment.
- [x] 3.2 Run `docker compose config` and `docker compose down -v && docker compose up --build` from the Dev Container; verify the application image builds and the Compose service starts with the existing runtime configuration.
- [x] 3.3 Perform a final documentation and working-tree review; verify the Docker-in-Docker configuration, lockfile, contributor guidance, and README consistently describe one supported verification environment without changing application runtime requirements.