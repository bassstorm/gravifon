## 1. Setup & Architectural Guardrails

- [x] 1.1 Add `org.jmolecules:jmolecules-ddd`, `org.jmolecules:jmolecules-onion-architecture`, and `org.jmolecules.integrations:jmolecules-archunit` to `pom.xml`, and verify `mvn test-compile` succeeds.
- [x] 1.2 Implement ArchUnit architecture test suite enforcing `JMoleculesDddRules.all()`, `JMoleculesArchitectureRules.ensureOnionSimple()`, and domain package isolation, verifying with `mvn test -Dtest=DomainArchitectureArchUnitTest`.

## 2. Polymorphic Domain Model & Value Objects

- [x] 2.1 Introduce typed identifiers `TrackId` and `PlaylistId` implementing `org.jmolecules.ddd.types.Identifier`, and verify unit tests pass.
- [x] 2.2 Refactor `Track` into a sealed interface with `FileTrack` and `StreamTrack` record implementations implementing `org.jmolecules.ddd.types.AggregateRoot`, and verify unit tests for track creation and freshness checks pass.
- [x] 2.3 Update `JpaTrackRepository` and `TrackEntity` mapping to translate to and from the sealed `Track` hierarchy (`FileTrack`, `StreamTrack`), and verify with `PersistenceRepositoryDataJpaTest`.
- [x] 2.4 Add `existsById` and `findAllById` to `TrackRepository` and `JpaTrackStore`, and verify repository tests pass.

## 3. Clock Elimination & Pure Policies

- [x] 3.1 Refactor `StreamRefreshService` to accept `StreamTrack` and explicit `Instant evaluationTime`, removing `Clock` field and test-only constructors; update `StreamRefreshServiceTest` to verify without clock mocking.
- [x] 3.2 Refactor `StreamRefreshScheduler` to remove all time math, repository queries, and `Clock` dependency, making it a thin trigger invoking `StreamRefreshService`; verify with updated `StreamRefreshSchedulerTest`.
- [x] 3.3 Remove `Clock` and test-only constructors from `JpaPlaylistRepository` and `JpaPlaybackStateRepository`, using `Instant.now()` at entity persistence boundaries; verify persistence tests pass.

## 4. Audio Streaming Relocation & Controller Thinning

- [x] 4.1 Relocate `StreamProxy` and `HttpStreamProxy` to `com.gravifon.player.streaming`, removing servlet and network delivery types from `registry.stream`; verify existing proxy tests pass in new package.
- [x] 4.2 Extract byte-range file streaming logic and playback observation out of `AudioStreamingController` into a dedicated `AudioStreamingService`; verify with unit tests and `AudioStreamingControllerWebMvcTest`.

## 5. Catalog Scanning & Registry Deconstruction

- [x] 5.1 Extract library scanning and startup reconciliation from `TrackRegistry` into a dedicated `LibraryScanCoordinator` application service; verify scan tests pass.
- [x] 5.2 Decouple `TrackRegistry` from direct `PlaylistRepository` dependency by passing referenced track ID queries through a clean port; verify `TrackRegistryTest` passes.
- [x] 5.3 Optimize `ScanReconciler` to use map lookups instead of $O(N \times M)$ nested filtering; verify `ScanReconcilerTest` passes.

## 6. Playlist & Playback Aggregate Encapsulation

- [x] 6.1 Optimize `PlaylistService.validateTracks` to use `trackRepository.findAllById` / `existsById` instead of `findAll()`; verify `PlaylistServiceTest` passes.
- [x] 6.2 Encapsulate playlist mutation operations (rename, setMode, addEntries, removeEntries, reorder) directly within the `Playlist` aggregate; verify unit tests pass.
- [x] 6.3 Encapsulate playback state machine rules and position tracking inside a dedicated `PlaybackSession` aggregate, removing mutable state and inner interfaces from `PlaybackService`; verify `PlaybackServiceTest` passes.
- [x] 6.4 Remove `PlaylistRepository` dependency from `JpaPlaybackStateRepository`; verify persistence and playback tests pass.

## 7. Constructor Consolidation & Boilerplate Reduction with Lombok

- [x] 7.1 Review all updated classes across services, repositories, and controllers to ensure single constructors, replacing explicit boilerplate constructors with Lombok `@RequiredArgsConstructor` / `@AllArgsConstructor` where applicable; verify checkstyle and compilation with `mvn test-compile`.

## 8. Verification & Clean Pipeline Gate

- [x] 8.1 Verify full project compilation, ArchUnit rules, and unit test suite pass with `mvn clean test`.
- [x] 8.2 Run full build with coverage gate verification via `mvn clean verify`.
