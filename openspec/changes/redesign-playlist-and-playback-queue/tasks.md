# Tasks

Prerequisite: the complete verified [session Phase 1](../introduce-client-sessions/tasks.md), [vertical-slice Phase 1.5](../replace-jmolecules-with-vertical-slices/tasks.md), and [domain types and boundary conventions](../enforce-domain-types-and-boundary-conventions/tasks.md). Follow [concrete design](./design.md#concrete-implementation-contract) and the capability deltas below. Paths are relative to `src/main/java/com/gravifon/player/` with mirrored tests. No legacy compatibility, jMolecules annotations, 1:1 pass-through repository interfaces, public queue-management producer, browser client, listening accounting or guaranteed delivery is included.

Groups 1-6 are **Phase 2**; groups 7-11 are **Phase 3**. Stop after group 6 for human review. Groups follow their prerequisites; each owns focused tests and directly relevant docs, not a final backlog of tests. Domain tests use real values; JPA filtering/rollback is verified with real persistence, not Mockito persistence simulations.

## 1. Phase 2 — Playlist domain

References: [playlist delta](./specs/playlist-management/spec.md) and named types/API sections of design.

- [ ] 1.1 Recreate `playlist/model` IDs/entries/SelectionMode/Playlist and `PlaylistSourceChanged`/`PlaylistDeleted` domain event records with stable entry identity distinct from order/track. Add `PlaylistTest` for duplicate tracks, exact reorder, insert-before/append/removal preserving survivors and explicit invalid direct calls. Verify no playback-package dependency via `DomainArchitectureArchUnitTest`.
- [ ] 1.2 Define dedicated create/catalog/update/insert/remove/reorder application command records under application ownership, separate from API DTOs, enforcing structural invariants in their compact constructors; default omitted mode through constrained `PlaylistProperties` (`gravifon.playlist.default-mode: SEQUENTIAL` without `${...}` aliases). Verify commands reject invalid direct collections/names and domain tests cover identity/order semantics, without configuration accessor tests.

## 2. Phase 2 — Queue and local source policy

Prerequisite: group 1. References: [queue delta](./specs/playback-queue/spec.md), initial-policy mechanics in design.

- [ ] 2.1 Create occurrence/association/policy/item/continuation/queue types with immutable ordered current/retained/generated state. Add `PlaybackQueueTest` for at most one current, placement policies, distinct repeated occurrences, retained precedence and valid empty states; verify source association is optional without track duplication.
- [ ] 2.2 Define SourceSnapshot/SourceChange/SourceSequenceUpdate and implement concrete `@Component` `SourceSequencePolicy` with controllable random/ID sources. Add focused `SourceSequencePolicyTest` for sequential looping/anchor, independent random picks, generated horizon configured to 3 (three source-managed pending choices, excluding current/retained), stored-choice stability, and documented source-change rules. Verify no repository I/O, 1:1 pass-through interface, or hidden mutable cursor.
- [ ] 2.3 Implement semantic queue binding, domain retained insertion, remove/current advancement, clearPending, source reconciliation and Stop suppression through policy invocation. Verify retained promotion does not reposition continuation/reroll, generated removal auto-refills, and no caller needs a second repair.
- [ ] 2.4 Add before/after source tests for multi-entry removal, detached stable anchor under reorder, empty-to-populated, mode edits and source deletion. Verify current/retained identity survives, associations detach and stopped pending stays empty; no public retained-item endpoint.
- [ ] 2.5 Add `PlaybackProperties` (`gravifon.playback.generated-horizon: 1` without `${...}` aliases) and the Phase 2 `PlaybackContext` container/intent/bookmark/revision foundation. Verify plain domain tests for canonical empty state, whole-context ownership and queue transitions with position tied to current. Update README architecture boundaries without advertising public playback commands yet.

## 3. Phase 2 — Whole-context and playlist persistence

Prerequisites: groups 1-2 and session user lock.

- [ ] 3.1 Create design DDL for playlist/entry/context/queue tables, indexes and safe optional associations in `V1__init.sql`; create their entities and Spring Data stores. Verify Flyway/Hibernate validation with isolated test database; no cascading source FK that prevents detachment and no unique ordinal constraint that fails row-by-row reorder.
- [ ] 3.2 Create concrete `@Repository` `PlaylistRepository` and `PlaybackContextRepository` classes exposing only domain models, scoped by user and saving queue only through context. Verify `PlaylistRepositoryDataJpaTest` and `PlaybackContextRepositoryDataJpaTest` for stable IDs/order, all segments, detached references, continuation and metadata sharing.
- [ ] 3.3 Implement stable-ID collection reconciliation rather than clear/recreate membership, and explicit context revision increments once per actual nested/scalar change. Verify reorder/queue mutations retain survivor IDs, nested change advances revision, reload does not reroll and no-op save/read does not advance revision.
- [ ] 3.4 Implement lazy context creation under existing user-row lock; missing-context reads produce empty revision-zero projection without persistence. Verify real persistence/domain boundary tests and document explicit development-database recreation for the new initial schema.

## 4. Phase 2 — Atomic mutations and registry reference safety

Prerequisite: group 3. References: [registry delta](./specs/track-registry/spec.md), transaction contract in design.

- [ ] 4.1 Implement `PlaylistService` user-scoped operations with user-first lock, complete validation before changes, and synchronous `PlaylistSourceChanged`/`PlaylistDeleted` event publication; implement `PlaybackSourceReconciliationListener` (`@EventListener` in `playback/service`) to reconcile and save the user's `PlaybackContext` in the same transaction. Verify strict unit tests for orchestration and `PlaylistMutationIntegrationTest` for real atomic rollback/ownership filtering; controllers must not chain mutations and `playlist` must not depend on `playback`.
- [ ] 4.2 Reconcile association detachment for current/retained items even when their edited source is not the current backing playlist; delete binding/generated/continuation only when backing source is deleted. Verify direct domain and persistence-backed deletion tests preserving current/progress and retained order.
- [ ] 4.3 Have `PlaylistRepository` and `PlaybackContextRepository` implement `ReferencedTrackIdsPort` directly using JPA queries across all users' entries and queue segments so `LibraryScanCoordinator` unions their results via `List<ReferencedTrackIdsPort>`. Verify missing-source scan tombstones detached/current/retained/generated references and removes genuinely unreferenced tracks; no fallback on lookup errors.
- [ ] 4.4 Add sorted track-row locking to `TrackRepository` using the derived query `@Lock(LockModeType.PESSIMISTIC_WRITE) findLockedByIdInOrderByIdAsc` on `JpaTrackStore` and apply it to all new reference-creation paths; adapt `LibraryScanCoordinator` REMOVE to lock/recheck references before delete/tombstone. Verify real persistence scan tests and reference invariants with retained scan-health behavior; no FK exception swallowed or all-user lock inversion.
- [ ] 4.5 Update README/related persistence guidance for owned playlists, shared registry references and atomic event-driven source edits. Verify documented behavior against group tests and preserve established framework-first/vertical-slice conventions.

## 5. Phase 2 — Playlist API and ordinary workflows

Prerequisite: group 4. References: [API delta](./specs/player-rest-api/spec.md), playlist route/DTO table.

- [ ] 5.1 Create separate playlist create/catalog/update/insertion/removal/order DTOs and response records, with nested Bean Validation and application mapping. Implement PlaylistController routes/statuses exactly as designed. Verify WebMvc 201/200/204 shapes, invalid permutations, missing/foreign 404 and absence of active flag/sourceUrls mutation.
- [ ] 5.2 Add transactional `PlaylistWorkflowIntegrationTest` using MockMvc real sessions/services: create with duplicates, catalog snapshot, insert-before, reorder, rename/preference, remove/delete and independent users. Reuse the [Phase 1 fixture setup](../introduce-client-sessions/design.md#shared-audio-fixtures-and-integration-library-setup): copy valid tagged resources into a temporary library, scan through the real registry inside rollback-managed setup, and discover track IDs through the API. Verify returned entry IDs drive subsequent operations, shared metadata remains one registry value, database writes roll back and checked-in fixtures remain untouched.
- [ ] 5.3 Update README playlist API examples, mode-only-through-playlist rule, registry-registration-then-insertion and interim absent playback API. Verify examples against workflow tests; run targeted playlist/queue/persistence/API tests.

## 6. Phase 2 — Human review gate

- [ ] 6.1 Apply Java formatting and run `mvn clean test`/`mvn clean verify` with coverage/architecture intact. Verify container-sensitive schema/startup changes in Dev Container with explicit disposable storage recreation. Confirm retained session/registry/audio behavior and intentional absence of playback protocol/proactive scheduling.
- [ ] 6.2 Review Phase 2 acceptance and handoff: whole-context authority, source-policy tests including generated horizon configured to 3 and empty output, stable IDs, real ownership/reference protection and atomic rollback. Verify preceding task evidence and stop for human approval before starting Phase 3.

## 7. Phase 3 — Context transitions, evidence and request digest

Prerequisite: Phase 2 review approval. References: [playback delta](./specs/playback-control/spec.md) and applicability table.

- [ ] 7.1 Create InstructionRef/ExecutionState/CheckpointKind/ExecutionFailure/ExecutionObservation/ExpectedPlayback and dedicated application command records, extending context with authority/instruction/evidence. Verify plain `PlaybackContextTest` for separate intent/execution, advisory millisecond position, failure diagnostic reset and invalid direct input.
- [ ] 7.2 Implement Play/Pause/Stop/play-from-entry/Next/Retry transitions and actual-versus-redundant takeover instruction rules. Verify the applicability table, same-occurrence resume, Stop replay fresh occurrence, no-current errors, failed Retry, retained preservation and normalization as one instruction.
- [ ] 7.3 Implement atomic checkpoint validation and natural Advance using exact current/instruction/expected successor, including no successor. Verify domain tests reject stale evidence without partial mutation, above-duration reports remain valid, exhaustion differs from Stop and no preparation/reservation mechanism appears.
- [ ] 7.4 Implement pure PlaybackRequestDigest with SHA-256 of fixed canonical application request fields and operation discriminator. Verify digest tests for decoded whitespace/property-order equivalence, null/false defaults, sequence/content differences, route distinctions, and excluded correlation/receipt time; no global mapper side effects.

## 8. Phase 3 — Durable protocol and lifecycle coordination

Prerequisite: group 7.

- [ ] 8.1 Add protocol context columns and playback-owned write-state entity/store and concrete `@Repository` `PlaybackWriteStateRepository` per schema. Verify `PlaybackWriteStateRepositoryDataJpaTest` for last-applied state, historical instruction surviving session cleanup and whole-context reload without restart normalization.
- [ ] 8.2 Implement PlaybackWriteCoordinator user-locked validity admission, applied-retry recognition before takeover/preconditions, all command/checkpoint validation, transition and atomic context/dedup save. Verify strict focused coordinator tests plus real persistence rollback; rejected writes never record sequence/digest and no-op accepted writes retain instruction/revision.
- [ ] 8.3 Add synchronous PlaybackAuthorityLifecycleListener joining session publisher transaction and conditionally clearing matching pointer. Invoke session expiration handling for stored active sessions during reads/writes; verify expiry admission with focused unit tests. Verify `PlaybackLifecycleIntegrationTest` through explicit application invalidation for pointer/revision change only, preserved facts and subsequent 401; unit-test delayed invalidation events never clearing a newer player. Keep real time and disable cleanup scheduling in integration tests: do not execute jobs or replace/advance the Clock. Repository slices verify session deletion preserves historical instructions without invoking cleanup jobs.
- [ ] 8.4 Implement canonical PlaybackService read/project operation with persisted revision and caller authority, no horizon refill or synthetic current. Verify coherent reload and empty-context read behavior through persistence tests; no default session or runtime restore runner.
- [ ] 8.5 Update schema/lifecycle documentation, explaining exact revision/dedup ownership, cleanup and disposable-database recreation. Verify group tests and retained session renewal without dedup overwrite; no dedicated race/network harness.

## 9. Phase 3 — Typed playback API and normal client stories

Prerequisite: group 8.

- [ ] 9.1 Create separate command DTOs, expected/checkpoint and failed/nonfailed observation variants, application mapping, PlaybackSnapshot/WriteResponse and PlaybackController routes. Verify nested validation and exact 200/409 outcome/snapshot and 400/401/404 error shapes with WebMvc tests; no revision precondition, null-bypass regex or generic command language.
- [ ] 9.2 Add transactional PlaybackWorkflowIntegrationTest with MockMvc real sessions/services: acquire/create/play-from-entry, report Preparing/Playing, seek discontinuity, Pause checkpoint, resume, prefetch next and Advance, report new instruction, Stop/replay. Reuse Phase 1 tagged fixtures copied into a temporary library and real scan-to-H2 setup; build all session/playlist/playback state through API operations. Verify returned IDs/snapshots drive all steps without internal state injection, database writes roll back and checked-in fixtures remain untouched.
- [ ] 9.3 Extend normal workflows for deliberate same-user takeover, new-user independent playback and backing playlist edits preserving current. Keep sequence/stale/invalid checkpoint matrix in focused tests, not an outer adversarial suite. Verify no new client producer or audible-gaplessness claim.
- [ ] 9.4 Update README with canonical command/observation examples, integer-ms/advisory duration, outcome/error table, ordering-only revision, and optional-retry/best-effort semantics. Update playback correlation guidance to occurrence/playlist-operation vocabulary. Verify API examples against workflows and correlation propagation tests.

## 10. Phase 3 — Restore proactive refresh integration

Prerequisite: groups 8-9; retained refresh engine is not redesigned. References: [audio delta](./specs/audio-streaming/spec.md).

- [ ] 10.1 Add eligible-upcoming projection to `PlaybackContextRepository`, then create `UpcomingTrackRefreshAdapter` over all users' Play/Pause contexts with current; bound actual current/retained/generated per context, preserve queue duplicates and deduplicate shared work before refresh service. Verify the projection through real repository tests and adapter tests for Stop exclusion, count cap/default horizon two-choice coverage, multiple users and one shared refresh.
- [ ] 10.2 Restore `StreamRefreshScheduler` using the adapter and existing `${gravifon.stream.refresh-cron}`/look-ahead settings, releasing projection transactions before external refresh. Verify focused scheduler/planner/refresher tests for no startup run, disabled cron and retained request-time/failure behavior; do not add policy selection to refresh.
- [ ] 10.3 Update README refresh coverage/configuration examples to actual-choice maximum rather than guaranteed ten tracks. Verify docs match adapter tests and retained audio/range/proxy behavior remains free of position observations.

## 11. Phase 3 — Final server integration gate

- [ ] 11.1 Run formatting, `mvn clean test` and `mvn clean verify`, keeping domain architecture, isolated H2/Flyway validation and coverage checks active. Perform container-sensitive clean startup verification in Dev Container with approved disposable storage; verify session/playlist/playback/audio/health normal workflows.
- [ ] 11.2 Review all owned spec scenarios against task evidence and explicit deferred client/race/network scope. Verify no legacy routes, singleton/dual authority, byte-derived position, prepared handoff, optional logging token leak or unowned delta remains. Mark implementation tasks only after verified behavior; propose archival separately, not during implementation verification.
