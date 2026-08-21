## Context

Nomenclature drifted across metadata layers (see proposal.md - Why). This change is editorial cleanup plus a README restructuring; no runtime behavior changes. The decisions below were settled during exploration and recorded here so implementation is mechanical.

## Goals / Non-Goals

**Goals:**
- One canonical product descriptor used consistently across README, pom.xml, and Dockerfile.
- README reduced to intent, high-level architecture, and repo usage; operational detail lives in specs.
- "MVP" removed from stable specs, user-facing strings, and code identifiers; retained only in archived change history.
- Single build/verify command set that works identically inside and outside containers.

**Non-Goals:**
- No behavior changes to endpoints, status codes, or state handling.
- No edits to archived changes under `openspec/changes/archive/` — history stays frozen.
- No new documentation files; AGENTS.md stays lean and is not expanded.

## Decisions

### 1) Canonical descriptor: `Local-network audio player`
- **Rationale:** Describes the product capability without coupling identity to a client implementation. "Browser UI" phrasing was rejected because the REST contract is deliberately client-agnostic (see `player-rest-api` spec); Android/desktop clients must not make the tagline wrong.
- **Alternatives considered:** "Local-network audio player server" (accurate but off-putting and client-irrelevant); "Self-hosted local-network audio player" ("self-hosted" is marketing noise).
- **Applied to:** README H1/tagline, `pom.xml` `<description>`, Dockerfile OCI `description` label. No trailing period (tagline typography).
- The existing `index.html` subtitle "Local-network audio player" already matches; unchanged.

### 2) "MVP" removal strategy by layer
- **Stable specs:** "MVP" is stripped editorially via MODIFIED deltas; requirement semantics are identical, so no behavior re-validation is implied.
- **User-facing strings:** 501 message becomes `Playlist mutations are not yet implemented` — states the fact, no version-state framing.
- **Code identifiers:** `GravifonMvpIntegrationTest` → `GravifonIntegrationTest` (pure rename).
- **Archived changes:** untouched. MVP was accurate at that point in history.
- **Rationale:** MVP is a transitional state, not an identity. Once archived, the qualifier in stable artifacts is noise that becomes a lie the moment scope grows.

### 3) README scope: intent + usage, not operations manual
- **Removed:** MVP Access Scope section (constraint lives in `player-rest-api` spec), "Why `build:` was removed from Compose" (historical justification is noise for readers), Docker parameter tables and tag-override examples (belong to `containerized-runtime` spec), links to AGENTS.md and to specific spec files.
- **Kept:** architecture diagram and bounded-contexts line, build/verify/run commands, conventions summary (coverage, mocking, logging severity table).
- **Rationale (link removal):** Harnesses load AGENTS.md naturally; humans read README. Cross-linking implies a peer relationship that doesn't exist. Spec pointers become stale maintenance burdens — the openspec-first principle statement ("specs/ = status quo, changes/ = evolution") is sufficient because anyone who needs specs knows where they live.
- **Alternatives considered:** Curated spec link list — rejected as a maintenance tar pit. Moving conventions into AGENTS.md — rejected; README is the human-facing conventions home, AGENTS.md is the agent-facing one.

### 4) Build commands: `mvn clean verify` and `mvn clean verify -Pdocker` everywhere
- **Rationale:** One command for both container and host execution. `clean` prevents stale-incremental confusion when commands are run blindly. `verify` includes the JaCoCo gate. `install` was rejected: the `~/.m2` copy is useless for a containerized app, and maintaining two command variants wastes more (confusion, tokens) than the redundant local-repo write would save.
- **Alternatives considered:** `mvn clean install -Pdocker` (wasteful, no benefit); `mvn package -Pdocker` (skips tests).

### 5) Spec Purpose edits are direct, not deltas
- The `playlist-management` Purpose line mentions MVP. Per OpenSpec rules, delta specs cannot carry Purpose changes for existing capabilities — the main spec file is edited directly as part of implementation.

## Risks / Trade-offs

- **[501 message change breaks a strict client assertion]** → Mitigate by treating it as contract-touching: test expectations updated in the same change; message wording is documented in `player-rest-api`/`playlist-management` specs.
- **[README no longer self-documents Docker run parameters]** → Acceptable: `containerized-runtime` spec is the source of truth; README links the principle, not the parameters.
- **[Class rename breaks external references]** → None exist (test class, repo-internal); verify with full `mvn clean verify`.
