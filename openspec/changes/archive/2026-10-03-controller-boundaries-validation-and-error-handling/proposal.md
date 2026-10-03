## Why

As part of codebase modernization and adopting Spring Boot best practices along with improving application design, several presentation-tier and domain-boundary violations need to be resolved. Currently, `PlaylistController` is annotated with `@Transactional` and orchestrates multi-step persistence actions directly, request validation is performed imperatively with ad-hoc checks and raw `IllegalArgumentException`s, enums in path variables are manually parsed via string manipulation and `valueOf`, and `ApiExceptionHandler` lacks handlers for standard Spring Web MVC exceptions (`MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`), which can cause client errors to bubble up as internal 500 responses. Furthermore, `TrackRegistry` methods throw `IllegalArgumentException` instead of domain-appropriate missing entity exceptions when referencing non-existent tracks, erroneously producing 400 Bad Request instead of 404 Not Found at the API layer.

Modernizing controller boundaries, adopting declarative Bean Validation, delegating atomic transactions strictly to service methods, using standard Spring MVC enum path variable conversion, and hardening global exception handling ensures robust API contracts, prevents database connection leaks in the presentation tier, and guarantees consistent REST error responses.

## What Changes

- **Controller Boundaries & Service-Layer Transactions**:
  - Remove `@Transactional` annotations from `PlaylistController`.
  - Introduce atomic, service-level transaction methods in `PlaylistService` (e.g. dedicated playlist creation and mutation orchestration) so multi-step mutations (resolving stream URLs, reordering, adding/removing entries, setting mode) execute in a single `@Transactional` boundary rather than across multiple controller-driven transactions.
  - Ensure `StreamTrackRegistrar` operations execute under explicit transaction boundaries.
- **Declarative Bean Validation and Focused Presentation DTOs**:
  - Replace overloaded multipurpose request DTOs with single-responsibility models (`PlaylistCreateRequest` and `PlaylistUpdateRequest`), eliminating validation group workarounds (`Create.class`), regex null-bypass hacks (`@Pattern(regexp = ".*\\S.*")`), and duplicated presentation/domain validation.
  - Refactor `TrackStateReportRequest` to cleanly represent error reporting versus state clearing without reliance on `@Pattern` null hacks.
  - Add nested container constraints to collection payloads (e.g. `@NotNull Map<@NotBlank String, @NotEmpty List<@NotBlank String>> metadata` in `TrackMetadataUpdateRequest`) to prevent unhandled `NullPointerException`s from causing 500 errors on invalid inputs.
  - Annotate `@RequestBody` parameters with `@Valid` across `PlaylistController`, `PlaybackController`, and `TrackController`.
- **Intention-Revealing Application Service APIs**:
  - Eliminate boolean flag parameters in application services (e.g. split `TrackRegistry.reportState(..., boolean clear)` into explicit `reportFailure(trackId, kind, message)` and `clearError(trackId)`).
  - Add explicit service methods in `TrackRegistry` (`getTrack(trackId)` and direct `Path resolveTrackPath(trackId)`) so controllers never construct domain exceptions or perform duplicate lookups.
  - Decouple catalog materialization and custom playlist creation at the service boundary.
- **Native Enum Path Conversion**:
  - Replace manual string parsing and `valueOf` in `PlaybackController` with typed `@PathVariable PlaybackMode mode` and `@PathVariable TransportState transportState`, leveraging Spring's standard enum conversion and adapting test cases to use standard enum naming.
- **Robust Exception Handling in `ApiExceptionHandler`**:
  - Handle `MethodArgumentNotValidException` and `HandlerMethodValidationException` to return 400 Bad Request with actionable field error details.
  - Handle `MethodArgumentTypeMismatchException` (e.g. invalid enum or ID path parameters) to return 400 Bad Request instead of bubbling up to 500.
  - Handle `HttpMessageNotReadableException` (malformed JSON or unparseable request body) to return 400 Bad Request.
  - Handle `HttpRequestMethodNotSupportedException` to return 405 Method Not Allowed.
- **Consistent Entity Not Found Semantics Across Boundaries**:
  - Update `TrackRegistry` and service callers to throw `EntityNotFoundException` in the domain error ring instead of misleading `IllegalArgumentException`s or HTTP-level `ResourceNotFoundException`, ensuring 404 Not Found is returned consistently across the entire API surface.
- **Complete WebMvc API Slice Test Coverage**:
  - Add comprehensive slice tests in `TrackControllerWebMvcTest` for `PATCH /api/tracks/{trackId}/metadata` and `POST /api/tracks/{trackId}/state` covering success, failure state recording, clearing state, validation errors, and 404 on missing tracks.
  - Add validation error tests in `PlaylistControllerWebMvcTest` (e.g. blank name, catalog with entries) and missing entity scenarios.
  - Add validation tests in `PlaybackControllerWebMvcTest` for position reporting bounds and enum conversion errors.
  - Verify `ApiExceptionHandler` returns standard `ApiErrorResponse` payloads for all handled error categories.
- **Documentation & Conventions**:
  - Update `openspec/specs/player-rest-api/` spec with explicit validation rules and error response contracts.
  - Record controller, transaction boundary, Bean Validation, and error-handling conventions in `AGENTS.md`.

## Capabilities

### New Capabilities
<!-- None: web layer and controller boundary modernization -->

### Modified Capabilities
- `player-rest-api`: Defines declarative validation contracts, 400 Bad Request handling for invalid inputs/mismatches/malformed JSON, and consistent 404 Not Found responses for missing track resources.

## Impact

- **Web Controllers & DTOs**: `PlaylistController`, `PlaybackController`, `TrackController`, `ApiExceptionHandler`, and request models in `com.gravifon.player.api.model`.
- **Services**: `PlaylistService`, `StreamTrackRegistrar`, and `TrackRegistry`.
- **Web MVC Tests**: `TrackControllerWebMvcTest`, `PlaylistControllerWebMvcTest`, `PlaybackControllerWebMvcTest`, and dedicated exception handling tests.
- **Documentation & Specifications**: `AGENTS.md`, `openspec/specs/player-rest-api/spec.md`, and `SPRING_BOOT_REVAMP_PLAN.md`.
