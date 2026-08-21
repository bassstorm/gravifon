## Why

Project nomenclature drifted across metadata layers: the app is described five different ways ("browser audio player", "local-network audio player server", etc.), and "MVP" — a transitional state, not an identity — leaked from archived change history into stable specs, user-facing strings, and code identifiers. The README accumulated operational noise (Docker mechanics essays, historical justifications) that duplicates or contradicts the openspec-first principle.

## What Changes

- **Canonical product descriptor**: `Local-network audio player` (no period, no "browser", no "MVP").
- **README.md**: Reduce to intent, high-level architecture, and repo usage (build/verify/run commands, conventions). Remove MVP Access Scope section, Docker operational essays, "Why build: was removed" justification, and cross-links to AGENTS.md or specific specs.
- **pom.xml**: `<description>` → `Local-network audio player`.
- **Dockerfile**: OCI label → `Local-network audio player`.
- **PlaylistController**: 501 message → `Playlist mutations are not yet implemented` (removes "MVP").
- **Tests**: Update `PlaylistControllerWebMvcTest` expectations; rename `GravifonMvpIntegrationTest` → `GravifonIntegrationTest`.
- **Stable specs**: Strip "MVP" qualifiers from requirements in `player-rest-api`, `playlist-management`, `media-library-catalog`, `playback-control`, and `agent-first-project-documentation`. Behavior does not change; only transitional wording is removed.
- **Build commands**: Standardize on `mvn clean verify` and `mvn clean verify -Pdocker` (single command for both containerized and host execution).

## Capabilities

### New Capabilities
<!-- None — this is a cleanup/refactoring change with no new behavior. -->

### Modified Capabilities
- `player-rest-api`: Remove "MVP" qualifiers from access-model and endpoint-consistency requirements; behavior unchanged.
- `playlist-management`: Remove "MVP" qualifiers from read-only view and mutation-rejection requirements; behavior unchanged.
- `media-library-catalog`: Remove "MVP" qualifier from track-metadata requirement; behavior unchanged.
- `playback-control`: Remove "MVP" qualifier from random-mode requirement; behavior unchanged.
- `agent-first-project-documentation`: Remove "MVP" qualifiers from operational-behavior documentation requirements; behavior unchanged.

## Impact

- **User-facing**: README, 501 error messages, HTML subtitle (unchanged), image metadata.
- **API contract**: 501 response body text changes (wording only; status code and semantics identical).
- **Code**: Class rename (`GravifonMvpIntegrationTest`), string literal updates.
- **Specs**: Editorial cleanup only; no requirement semantics change.
- **Build**: README examples standardized on `clean verify` lifecycle.
