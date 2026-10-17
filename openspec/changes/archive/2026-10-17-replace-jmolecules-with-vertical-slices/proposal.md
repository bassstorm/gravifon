# Proposal

## Why

jMolecules was introduced early in the MVP to help structure domain and layer boundaries. Following the Phase 1 architectural reset, the codebase is organized into clear functional units using immutable Java records, and jMolecules no longer pays back its maintenance cost: it requires marker ceremony, silently skips unannotated classes, cannot enforce boundaries between functional slices, and encourages redundant pass-through interfaces and wrapper types. Executing a focused Phase 1.5 now—while the codebase is at its smallest footprint between Phase 1 and Phases 2–3—establishes self-describing vertical slices and lean YAGNI conventions before playlist and playback are rebuilt.

## What Changes

- Replace jMolecules annotations and marker interfaces with plain ArchUnit rules that enforce vertical functional slices and horizontal package purity directly from package structure.
- Apply YAGNI across the domain and persistence boundary: remove artificial wrapper types and duplicate accessors introduced only for jMolecules, collapse 1:1 repository interfaces and adapters into concrete repositories, and extract entity-mapping boilerplate into isolated, unit-tested converters.
- Standardize cross-slice communication on synchronous Spring events for atomic in-transaction reactions and multi-bean provider lists for cross-slice aggregation and strategy selection.
- Split architectural documentation by audience: high-level Mermaid diagrams and component responsibilities in `README.md` for human and machine orientation, and concrete implementation/boundary rules in `AGENTS.md` for developers and coding agents.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-first-project-documentation`: Update architectural, persistence, and documentation guidance to reflect annotation-free vertical slices, YAGNI repository/converter conventions, hybrid cross-slice collaboration, and audience-tailored `README.md` / `AGENTS.md` documentation.

## Impact

- Executes as **Phase 1.5** between `introduce-client-sessions` (Phase 1) and `redesign-playlist-and-playback-queue` (Phases 2 and 3).
- Simplifies internal domain models, repositories, scan reference wiring, architecture tests, and project documentation with zero changes to HTTP APIs, database schema, or runtime behavior.
