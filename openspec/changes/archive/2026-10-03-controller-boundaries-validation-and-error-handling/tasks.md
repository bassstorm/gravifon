## 1. Domain Exception Harmonization

- [x] 1.1 Introduce `EntityNotFoundException` in `com.gravifon.player.error` and migrate callers of `ResourceNotFoundException` (`PlaylistService`, `PlaybackService`, `AudioStreamingController`, `TrackController`) to `EntityNotFoundException`.
- [x] 1.2 Update `TrackRegistry` methods (`updateMetadata`, `reportState`, `resolveTrackPath`, `markStreamUnreachable`) to throw `EntityNotFoundException` when a track ID is missing, and verify unit tests in `TrackRegistryTest` pass.
- [x] 1.3 Add `getTrack(String trackId)` and refactor `resolveTrackPath(String trackId)` in `TrackRegistry` to return `Path` directly (throwing `EntityNotFoundException` when missing or unresolvable), removing domain exception instantiations and imports from `TrackController` and `AudioStreamingController`.
- [x] 1.4 Encapsulate track metadata normalization and error invariants into domain types/utilities (e.g. `TrackMetadata` and `TrackError` constructors), removing imperative validation loops from `TrackRegistry`.
- [x] 1.5 Enforce core invariants directly in the `Playlist` domain model (non-null `id`, non-blank `name` on construction and `rename()`), removing redundant `requireName` defensive checks from `PlaylistService`.

## 2. Service Transaction Boundaries

- [x] 2.1 Refactor `PlaylistService` to provide atomic creation and mutation operations (`createFromRequest`, `updateFromRequest`) coordinating track validation, stream registration via `StreamTrackRegistrar`, and playlist updates under a single `@Transactional` method.
- [x] 2.2 Remove `@Transactional` annotations from `PlaylistController` and delegate orchestration cleanly to `PlaylistService`.
- [x] 2.3 Verify `PlaylistServiceTest` and `StreamTrackRegistrarTest` pass with updated method signatures and transactional guarantees.

## 3. Bean Validation and Request DTOs

- [x] 3.1 Add standard Jakarta validation constraints (`@NotBlank`, `@PositiveOrZero`, `@NotNull`) to `PlaylistMutationRequest`, `PositionReportRequest`, `TrackMetadataUpdateRequest`, and `TrackStateReportRequest`.
- [x] 3.2 Add `@Valid` annotations to `@RequestBody` parameters across `PlaylistController`, `PlaybackController`, and `TrackController`.
- [x] 3.3 Verify model serialization and validation unit tests in `ApiModelMappingTest`.
- [x] 3.4 Split `PlaylistMutationRequest` into dedicated `PlaylistCreateRequest` and `PlaylistUpdateRequest` DTOs, removing validation groups (`Create.class`), regex `@Pattern` workarounds, and duplicate `@AssertTrue` checks in favor of authoritative `PlaylistService` business rule enforcement.
- [x] 3.5 Refactor `TrackStateReportRequest` to remove regex `@Pattern` null hacks, and eliminate boolean flag arguments in `TrackRegistry` by splitting `reportState(..., boolean clear)` into explicit `reportFailure(...)` and `clearError(...)` service methods.
- [x] 3.6 Add container-level validation to `TrackMetadataUpdateRequest` (`Map<@NotBlank String, @NotEmpty List<@NotBlank String>>`) and add defensive handling in `TrackRegistry.updateMetadata` to eliminate 500 `NullPointerException`s on malformed entries.

## 4. Native Enum Path Binding and Exception Handling

- [x] 4.1 Update `PlaybackController` path variables for `/api/playback/mode/{mode}` and `/api/playback/transport/{transportState}` to use typed `PlaybackMode` and `TransportState` enums instead of raw strings.
- [x] 4.2 Update test requests in `PlaybackControllerWebMvcTest` and `GravifonIntegrationTest` to send canonical uppercase enum strings.
- [x] 4.3 Add handlers in `ApiExceptionHandler` for `MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`, and `HttpRequestMethodNotSupportedException` mapping to standard `ApiErrorResponse` payloads.

## 5. WebMvc Slices and Verification

- [x] 5.1 Expand `TrackControllerWebMvcTest` to test `PATCH /api/tracks/{trackId}/metadata` and `POST /api/tracks/{trackId}/state` covering successful updates, validation failures, and 404 on missing tracks.
- [x] 5.2 Expand `PlaylistControllerWebMvcTest` to test validation errors (blank name, invalid catalog requests) and 404 on missing playlists.
- [x] 5.3 Expand `PlaybackControllerWebMvcTest` to test position report validation errors and invalid enum path conversions returning 400.
- [x] 5.4 Update `AGENTS.md` to document controller transaction boundary rules, Bean Validation conventions, and error handling practices.
- [x] 5.5 Run full verification via `mvn spotless:apply && mvn clean verify` and `openspec validate --all --no-interactive --json` to ensure clean build, architecture rules compliance, and valid OpenSpec artifacts.
