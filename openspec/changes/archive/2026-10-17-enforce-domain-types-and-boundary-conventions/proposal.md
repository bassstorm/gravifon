# Proposal

## Why

Before implementing the playlist and playback queue redesign, several cross-cutting implementation smells in the existing codebase—raw strings in domain models instead of rich standard/value types, scattered path and URL normalization, repeated defensive validation across internal layers, unnecessary string queries on persistence stores, and manual environment-variable placeholder aliases in configuration—should be eliminated universally so the upcoming redesign builds on a clean, consistent foundation.

## What Changes

- Use rich, opinionated domain and value types across domain models and application services (standard path and URI types for file and stream sources, typed metadata and username value objects, and specific track subtypes where the kind is already known), converting to and from raw strings or maps only at the HTTP presentation and persistence mapping boundaries.
- Unify file path and stream URL normalization into a single stateless normalization helper used consistently for both track identity derivation and canonical persistence representation, removing redundant per-resolver normalizer abstractions, manual character replacements in callers, and repeated identity hashing during scan reconciliation.
- Enforce a strict two-boundary validation model across the codebase: declarative validation on external HTTP requests and configuration properties, and self-validating domain/value records at construction time, removing duplicate null/blank/collection re-checks from internal application services, collaborators, and repositories.
- Replace unnecessary string queries in persistence stores with declarative Spring Data derived query methods (including pessimistic locking queries), reserving explicit queries for scalar projections and deferring specification executors until dynamic multi-criteria filtering is needed.
- Align application configuration property prefixes and keys with singular domain slice names so Spring Boot's native relaxed binding resolves environment variables directly without manual placeholder aliases in configuration files.
- Codify these five universal engineering conventions in the `agent-first-project-documentation` specification and project them to `AGENTS.md` (and `README.md` where applicable), while updating `openspec/config.yaml` strictly to require Mermaid diagrams instead of ASCII art, and align the pending playlist and playback queue redesign artifacts with them.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `agent-first-project-documentation`: Codify repo-wide conventions for rich domain types with boundary-only string conversion, unified source normalization, two-boundary validation without redundant service checks, derived Spring Data queries over string queries, and relaxed-binding-aligned configuration properties without placeholder aliases, projected to `AGENTS.md`/`README.md`, with `openspec/config.yaml` caring only about artifact/diagram formatting rules (Mermaid over ASCII).

## Impact

- Affects domain models, identity normalization, scanners, stream resolution/refresh, application services, repositories, converters, persistence stores, configuration properties, and API boundary mappings across the session, registry, streaming, persistence, config, and api slices.
- Updates `application.yml`, `application-local.yml`, `application-test.yml`, `openspec/config.yaml`, `README.md`, `AGENTS.md`, and the pending `redesign-playlist-and-playback-queue` planning artifacts so property names, diagram conventions, and architectural patterns stay consistent.
- Preserves all public HTTP route contracts, database schema columns, and container runtime environment variables (`GRAVIFON_CONFIG_DIR`, `GRAVIFON_LIBRARY_DIR`, `GRAVIFON_STREAM_*`, `GRAVIFON_SESSION_*`).
