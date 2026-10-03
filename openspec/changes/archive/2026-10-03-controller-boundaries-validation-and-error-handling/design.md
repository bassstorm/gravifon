## Context

See [proposal.md](proposal.md) for motivation. The application follows an Onion architecture verified by JMolecules and ArchUnit (`DomainArchitectureArchUnitTest`), where controllers reside in the presentation/infrastructure ring, services in the application/domain ring, and entities in the domain ring.

Currently:
1. `PlaylistController` carries `@Transactional` and orchestrates multi-step mutations imperatively.
2. Request validation is conducted with ad-hoc `if` checks in controllers and models throwing `IllegalArgumentException`.
3. `PlaybackController` uses raw String path parameters and invokes `PlaybackMode.valueOf(...)` manually.
4. `ApiExceptionHandler` lacks handlers for standard Spring Web exceptions (`MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`), leading to unhandled 500 Internal Server Errors when client input is invalid.
5. Missing entity queries in `TrackRegistry` throw `IllegalArgumentException`, resulting in 400 Bad Request instead of 404 Not Found.

## Goals / Non-Goals

**Goals:**
- Eliminate `@Transactional` from controllers; encapsulate playlist mutations and stream registrations into atomic service-level transactional boundaries.
- Introduce `EntityNotFoundException` in `com.gravifon.player.error` (`@DomainRing`) as the domain base exception for missing entities, with `ApiExceptionHandler` mapping it to HTTP 404.
- Apply declarative Bean Validation (`@Valid`, `@NotBlank`, `@PositiveOrZero`, etc.) to request models.
- Harden `ApiExceptionHandler` to map all Spring input validation and type mismatch errors to HTTP 400 with helpful, standard error responses.
- Replace manual string parsing of enums with Spring's native case-sensitive enum path variable conversion.
- Provide comprehensive WebMvc slice test coverage for all endpoints and error cases.

**Non-Goals:**
- Case-insensitive enum conversion or custom converter factories (clients and tests will use standard uppercase enum representations).
- Migrating to RFC 7807 `ProblemDetail` responses (Gravifon standardizes on `ApiErrorResponse`).
- Changing the public REST endpoint contracts or URIs.

## Decisions

### 1. Domain-Appropriate Missing Entity Exceptions and Invariant Encapsulation
- **Decision**: Introduce `EntityNotFoundException` in `com.gravifon.player.error` (annotated with `@DomainRing`), replacing `ResourceNotFoundException`. Have `PlaylistService`, `TrackRegistry`, and any domain/application services throw `EntityNotFoundException` when a requested entity ID does not exist.
- **Service Retrieval Methods**: Add explicit `getTrack(String trackId)` and refactor `resolveTrackPath(String trackId)` in `TrackRegistry` to return `Path` directly (throwing `EntityNotFoundException` when not found or non-resolvable). This allows controllers (`TrackController`, `AudioStreamingController`) to call services directly without importing or constructing domain exceptions.
- **Encapsulate Metadata, Error, and Playlist Invariants in Domain Types**: 
  - Move imperative metadata sanitization and blank-checking loops from `TrackRegistry` into domain types/utilities (e.g. `TrackMetadata` / `TrackError` constructor validation).
  - Enforce aggregate invariants directly on `Playlist` (non-null `id`, non-blank `name` on construction and `rename()`), removing manual `requireName` defensive checks from `PlaylistService`.
  - Application services (`PlaylistService`, `TrackRegistry`) remain clean, cohesive coordinators of persistence transactions and aggregate state transitions rather than manual Bean Validators.
- **Rationale**: Removes REST/HTTP vocabulary ("Resource") from the domain/application ring, standardizes service contracts to return entities directly, keeps controllers free of domain exception instantiation, and eliminates defensive validation spaghetti from application services.
- **Alternatives Considered**:
  - Keep `ResourceNotFoundException`: Retains HTTP-polluted nomenclature in domain models and services.
  - Granular `TrackNotFoundException` and `PlaylistNotFoundException` immediately: More boilerplate without immediate benefit since handling across all entities is uniform. Subclasses can be added later if entity-specific recovery logic is ever needed.

### 2. Service Transaction Boundaries & Atomic Playlist Mutation
- **Decision**: Remove `@Transactional` from `PlaylistController`. In `PlaylistService`, consolidate creation and update logic into atomic transactional methods (e.g. `createPlaylist(name, trackIds, sourceUrls, catalog, mode)` and `updatePlaylist(playlistId, mutation)`). Move `StreamTrackRegistrar.registerSources` invocation inside the service boundary (or coordinate atomically in `PlaylistService`).
- **Rationale**: Controllers must not hold open database transactions. Multi-step mutations (resolving streams, checking tracks, reordering, updating mode) must succeed or fail as a single atomic unit.
- **Alternatives Considered**:
  - Keep separate methods in `PlaylistService` and leave `@Transactional` on `PlaylistController`: Violates separation of concerns and holds database connections across presentation serialization.

### 3. Declarative Bean Validation with Dedicated DTOs and Container Constraints
- **Decision**: Split the overloaded `PlaylistMutationRequest` into two dedicated, intention-revealing presentation DTO records in `com.gravifon.player.api.model`:
  - `PlaylistCreateRequest`: `@NotBlank String name`, `List<String> trackIds`, `List<String> sourceUrls`, `Boolean catalog`, `PlaybackMode mode`. Invariant validation for catalog exclusivity resides in `PlaylistService` as the authoritative domain/application rule.
  - `PlaylistUpdateRequest`: optional `name`, `List<String> reorder`, `List<String> add`, `List<String> remove`, `PlaybackMode mode`. If `name` is provided, non-blank invariant is enforced at service/domain level or clean nullable validator without regex tricks.
- **Refactor `TrackStateReportRequest` & `TrackRegistry`**:
  - Differentiate state reporting: failure reporting requires a valid reason, whereas clearing state is an explicit action. In `TrackRegistry`, eliminate the boolean flag parameter by splitting `reportState(..., boolean clear)` into explicit `reportFailure(trackId, kind, message)` and `clearError(trackId)`.
- **Add Container Constraints to Nested Payloads**:
  - In `TrackMetadataUpdateRequest`: use `@NotNull Map<@NotBlank String, @NotEmpty List<@NotBlank String>> metadata` to prevent nulls from causing unexpected `NullPointerException`s inside `TrackRegistry.updateMetadata` that escape as 500 errors.
  - Make `TrackRegistry.updateMetadata` defensively ignore or cleanly process entries without raw `List.copyOf` NPE crashes.
- **Rationale**: Eliminates validation groups (`Create.class`), regex null-bypass workarounds (`@Pattern(regexp = ".*\\S.*")`), service boolean flag parameters, and unhandled collection NPE 500 crashes.
- **Alternatives Considered**:
  - Overloaded `PlaylistMutationRequest` and flag-driven `TrackStateReportRequest`: Causes rule duplication, validation leaks, and fragile regex tricks.

### 4. Hardened `ApiExceptionHandler`
- **Decision**: Add explicit `@ExceptionHandler` methods to `ApiExceptionHandler`:
  - `MethodArgumentNotValidException` -> 400 Bad Request (extracting binding errors and formatting a concise summary in `ApiErrorResponse.message()`).
  - `MethodArgumentTypeMismatchException` -> 400 Bad Request ("Invalid parameter: <name>").
  - `HttpMessageNotReadableException` -> 400 Bad Request ("Malformed JSON request body").
  - `HttpRequestMethodNotSupportedException` -> 405 Method Not Allowed.
  - `EntityNotFoundException` -> 404 Not Found.
- **Rationale**: Prevents Spring framework exceptions from escaping to generic `Exception` (which yields 500 Internal Server Error) and ensures consistent error payload format.

### 5. Native Enum Path Variables
- **Decision**: In `PlaybackController`, bind `@PathVariable PlaybackMode mode` and `@PathVariable TransportState transportState` directly. Update all integration and slice test calls to send uppercase enum values (`RANDOM`, `PLAYING`, `STOPPED`, `PAUSED`).
- **Rationale**: Leverages built-in Spring Web conversion, eliminating manual `valueOf` parsing and trimming.

## Risks / Trade-offs

- [Risk] Existing client scripts or tests calling `/api/playback/mode/random` with lowercase strings fail with 400.
  → Mitigation: Gravifon has no external clients yet; update all internal test suites (`GravifonIntegrationTest`, `PlaybackControllerWebMvcTest`) to use canonical uppercase values.
- [Risk] Moving stream registration inside `PlaylistService` introduces a dependency on `StreamTrackRegistrar`.
  → Mitigation: `StreamTrackRegistrar` is an application service (`@ApplicationRing`) and fits naturally alongside `PlaylistService` or as an injected collaborator.
