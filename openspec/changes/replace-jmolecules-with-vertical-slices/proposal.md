# Proposal

## Why

jMolecules (`jmolecules-ddd`, `jmolecules-onion-architecture`, and `jmolecules-archunit`) was introduced early in the MVP to manage complexity, but with the phased architectural redesign (`introduce-client-sessions` and `redesign-playlist-and-playback-queue`), the codebase is now organized into explicit, self-describing units of responsibility using plain Java 21 records. At this stage, jMolecules does not pay back: it requires ceremony (`package-info.java` ring annotations, per-class ring overrides in mixed packages, and `AggregateRoot`/`Identifier` markers on records), silently ignores unannotated classes, does not enforce vertical boundaries between functional slices, and encourages redundant 1:1 pass-through interfaces (`*Repository` + `Jpa*Repository`). Executing a focused Phase 1.5 now—while the codebase is at its smallest footprint immediately after Phase 1's selective reset—lets Phases 2 and 3 build directly on enforced vertical package slices and lean YAGNI conventions.

## What Changes

- Remove `jmolecules-ddd`, `jmolecules-onion-architecture`, and `jmolecules-archunit` dependencies from `pom.xml`.
- Remove all `package-info.java` ring files, `@DomainRing` / `@ApplicationRing` / `@InfrastructureRing` class annotations, and `AggregateRoot` / `Identifier` marker interfaces from production code.
- Replace `JMoleculesDddRules` and `JMoleculesArchitectureRules` in `DomainArchitectureArchUnitTest` with explicit, annotation-free ArchUnit rules that enforce:
  - **Vertical package slices** (`session`, `registry`, `playlist`, `playback`, `streaming`, plus shared edge packages `api`, `persistence`, `config`, `observability`, `error`) and their allowed one-way dependency matrix.
  - **Horizontal package purity** by package naming convention (`..model..` and `..identity..` remain free of Spring, Jakarta, persistence, and web dependencies; `..service..` remains free of web/API types and raw `persistence` JPA entities/Spring Data stores).
- Apply a **YAGNI single-implementation rule** to internal components:
  - Collapse redundant 1:1 repository port/adapter pairs (`TrackRepository` + `JpaTrackRepository`, `UserRepository` + `JpaUserRepository`, `ClientSessionRepository` + `JpaClientSessionRepository`) into single concrete `@Repository` classes in `<slice>/repository` whose public methods accept and return only domain models while encapsulating `persistence` stores/entities internally.
  - Retain interfaces only where multiple production/test implementations or cross-slice plug-ins exist (`StreamResolver`, `ReferencedTrackIdsPort`, `LibraryScanner`, `MetadataExtractor`, `TagWriteBackHandler`, `StreamProxy`, `MusicLibraryProbe`).
- Standardize cross-slice decoupling on a hybrid **Spring Events + Multi-Bean Provider** pattern:
  - **Synchronous in-transaction events (`@EventListener`)** for atomic cross-slice state reactions under the caller's transaction and lock boundary (such as session invalidation or playlist source changes/deletions).
  - **Multi-bean collection injection (`List<ReferencedTrackIdsPort>`)** in `LibraryScanCoordinator` for pull aggregation across slices, eliminating `NoDurableTrackReferencesAdapter` and allowing any slice with durable track references to register its own provider bean without coupling `registry` to downstream slices.
- Document the vertical slice architecture, interaction patterns, and YAGNI conventions in `README.md` (high-level architecture and slice overview for human readers) and `AGENTS.md` (concrete package, dependency, event, and testing rules for agent/developer implementation).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-first-project-documentation`: Replace jMolecules ring terminology with annotation-free vertical package slices enforced by ArchUnit, cross-slice event/provider interaction patterns, YAGNI single-implementation repository/service conventions, and tailored human (`README.md`) versus agent (`AGENTS.md`) architectural documentation.

## Impact

- Executes as **Phase 1.5** immediately after `introduce-client-sessions` (Phase 1) and before `redesign-playlist-and-playback-queue` (Phases 2 and 3), while legacy `playlist` and `playback` code is absent and the codebase surface is minimal.
- Modifies `pom.xml`, `DomainArchitectureArchUnitTest`, `Track`, `TrackId`, `UserId`, `ClientSessionId`, `TrackRepository`, `UserRepository`, `ClientSessionRepository`, `LibraryScanCoordinator`, `README.md`, `AGENTS.md`, and deletes all `package-info.java` files, `JpaTrackRepository`, `JpaUserRepository`, `JpaClientSessionRepository`, and `NoDurableTrackReferencesAdapter`.
- Preserves all HTTP APIs, database schemas (`V1__init.sql`), transaction/locking behavior, and test coverage from Phase 1 without behavioral regressions.
