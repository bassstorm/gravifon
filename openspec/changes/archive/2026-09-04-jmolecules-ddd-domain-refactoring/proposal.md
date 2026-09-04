## Why

The current domain architecture suffers from blurred boundaries, poor cohesion, and testing anti-patterns:
- `Track` is a single bag-of-fields record with mutually exclusive nullable fields for file and stream tracks.
- Direct dependencies on `Clock` and multi-constructor scaffolding exist solely for unit test time-stubbing.
- Infrastructure and delivery concerns (e.g., HTTP servlet proxying, range serving, disk scanning) are coupled directly into catalog services and controllers.
- Anemic domain models and stateful service singletons lead to algorithmic inefficiencies (such as scanning the entire catalog to validate playlist tracks) and fragmented state ownership.

Adopting jMolecules DDD with Java 21 features establishes explicit domain models, typed identifiers, and clean separation between pure domain rules, application services, and infrastructure adapters, while eliminating test-induced clock scaffolding.

## What Changes

- **jMolecules DDD & Onion Architecture Guardrails**: Add `jmolecules-ddd`, `jmolecules-onion-architecture` annotations, and automated ArchUnit rule enforcement (`ensureOnionSimple()`, `dddRules()`, domain package isolation) to verify architectural consistency and guard domain purity.
- **Polymorphic Track Hierarchy**: Replace the bag-of-fields `Track` record with a Java 21 sealed interface `Track` permitted to `FileTrack` and `StreamTrack`, supported by typed `TrackId`.
- **Pure Time Evaluation**: Eliminate `Clock` dependencies and test-only constructors across schedulers, services, and repositories; pass explicit `Instant` evaluation timestamps to pure domain policies.
- **Decompose TrackRegistry and Scanning**: Extract startup scan orchestration into a dedicated application service and decouple catalog storage from playlist querying.
- **Rich Domain Aggregates for Playlists and Playback**: Encapsulate playlist mutations and playback state machine transitions into rich domain models with typed `PlaylistId` and `TrackId`.
- **Relocate Audio Delivery & Thin Controllers**: Move stream proxying and byte range streaming out of the registry domain and controllers into dedicated delivery/streaming components.
- **Repository Optimization**: Add targeted ID existence queries (`existsById`, `findAllById`) to eliminate full-catalog scans during playlist validations.

## Capabilities

### New Capabilities
<!-- None: this is an internal architectural and domain refactoring -->

### Modified Capabilities
<!-- None: externally observable behavior and REST API contracts are preserved without changes to functional requirements -->

*(Note: `skip_specs: true` is configured in `.openspec.yaml` as this is an internal structural refactoring preserving existing spec requirements.)*

## Impact

- **Dependencies**: Adds `org.jmolecules:jmolecules-ddd`, `org.jmolecules:jmolecules-onion-architecture`, and `org.jmolecules.integrations:jmolecules-archunit` (test).
- **Domain Models**: Replaces `Track` record with sealed `Track` hierarchy (`FileTrack`, `StreamTrack`), introduces `TrackId` and `PlaylistId` value objects.
- **Services & Schedulers**: Refactors `StreamRefreshScheduler`, `StreamRefreshService`, `TrackRegistry`, `PlaylistService`, and `PlaybackService` to separate pure domain logic from application orchestration.
- **Delivery**: Moves `StreamProxy` and `HttpStreamProxy` out of `registry.stream` to a streaming delivery package.
- **Tests**: Replaces complex Mockito stubs, fake DB simulations, and fixed clocks with straightforward domain unit tests and ArchUnit architectural rules.
