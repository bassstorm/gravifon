# Proposal

## Why

Several infrastructure concerns are hand-rolled where Spring Boot already provides idiomatic, better-tested equivalents, and some carry latent correctness bugs: startup blocks on a library scan that bypasses its own transaction; configuration is a mutable bag of strings with duplicated defaults; byte-range handling and the upstream stream proxy re-implement framework features (with RFC-non-compliant `416` responses and hardcoded timeouts); stream URL refresh never actually acts ahead of expiry; and the Actuator dependency ships without any health semantics or container wiring. No backward compatibility or migration constraints exist, so each concern can move directly to the cleanest idiomatic design.

## What Changes

- **Startup lifecycle**: the library scan and playback-state restore move out of bean initialization onto Spring Boot's startup mechanisms, so the service starts on its persisted state while the scan reconciles in the background.
- **Configuration**: the single mutable properties bean becomes small, immutable, validated, per-consumer records that own their defaults once.
- **Range streaming**: byte-range parsing, `416` responses, and content-type resolution use Spring Web abstractions, with standards-compliant error responses and an explicit single-range contract.
- **Upstream stream proxy**: the proxy uses Spring's `RestClient`, with timeouts moved into configuration and upstream error statuses still passed through.
- **Stream refresh**: refresh becomes expiry-aware (fixing a defect where the look-ahead window never triggered a refresh), is decomposed into small, independently testable components, and runs on a managed executor instead of the JVM common pool.
- **Track failures**: failure reporters and construction become typed instead of string- and boilerplate-based.
- **Operational health**: explicit health, liveness, and readiness semantics, a startup diagnostic for the music library mount, and a container health check.
- **Breaking operator-facing change**: a small set of configuration keys is renamed, replaced, or added (including a cron-based refresh schedule that replaces the interval and enabled flag); details are in `design.md` (D3).

## Capabilities

### New Capabilities
- `service-health`: Operational health surface of the service — Actuator endpoint exposure, database and music-library-mount health indicators, readiness semantics (database + mount), process-only liveness semantics, and non-degrading scan-outcome observability.

### Modified Capabilities
- `track-registry`: The startup scan becomes asynchronous — the service starts on the persisted registry snapshot and reconciles the mounted library in the background after startup, instead of scanning synchronously during bean initialization.
- `audio-streaming`: Invalid-range handling is strengthened to RFC 7233-compliant `416` responses with a `Content-Range: bytes */<length>` header, multipart range requests are explicitly rejected as out of contract, and stream URL refresh becomes expiry-aware (proactive look-ahead refresh and a minimum remaining validity when serving).
- `containerized-runtime`: The runtime gains a container health check driven by the service health endpoint, and runtime documentation covers the operational endpoints.

## Impact

- **Lifecycle & configuration**: startup components, `GravifonApplication`, `application.yml`, and the configuration consumers across the library scan, track registry, and stream proxy/refresh code.
- **Streaming**: `AudioStreamingService`, `HttpStreamProxy`, and the stream refresh components (now split across `registry.stream` and `streaming`).
- **Playback**: a narrow read-only "upcoming tracks" query on the playback side to drive proactive refresh.
- **Domain model**: `StreamTrack` freshness policy, `TrackError` construction.
- **Operations**: health indicators, `docker-compose.yaml`, `README.md`.
- **Documentation & specifications**: `AGENTS.md` conventions (lifecycle, configuration, HTTP client, health); spec deltas for `track-registry`, `audio-streaming`, `containerized-runtime`; new `service-health` spec.
- **Operator impact**: the configuration key changes listed in `design.md` (D3); API contracts are preserved. Behavioral deltas: asynchronous startup-scan timing, stricter `416` responses, proactive and minimum-validity stream URL refresh, and the new health endpoints.
