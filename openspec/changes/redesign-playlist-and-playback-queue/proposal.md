# Proposal

## Why

Playback currently identifies playlist occurrences by track ID and maintains overlapping in-memory and persisted state, making duplicates, playlist edits, and persistence failures inconsistent. Introduce explicit playlist-entry and playback-occurrence identities and a durable queue so selection, reconciliation, and playback transitions have clear, independently testable responsibilities.

## What Changes

- **BREAKING** Give playlist entries stable IDs distinct from shared track IDs. Selection and membership operations address entries; strict reorder rearranges existing entries without changing membership.
- Keep playlists as named, ordered entry collections with a stored sequential/random mode preference. Playback interprets that preference; playlist domain code does not depend on playback implementation.
- Change mode only through playlist mutation; no playback-mode command or authoritative queue mode copy. Insert shared track IDs returned by the prerequisite registry registration operation, not source URLs resolved inside playlist mutations.
- Introduce a persisted playback queue containing current and pending occurrences, backing-source configuration, and continuation state. Occurrences have independent identities, optional source associations, and explicit source-managed, retained, or current policies.
- Reconcile playlist edits without interrupting current playback: detach a removed current entry's source association, remove eligible pending occurrences of removed entries, and rebuild eligible successors when selection rules change.
- Deleting the backing playlist detaches current and retained occurrences, discards source-managed pending choices, and clears source binding/continuation without stopping current playback.
- Encapsulate source-managed maintenance behind semantic queue/source operations. The policy reconciles the configurable horizon, defaults to the simplest implementation, and may return an empty segment. Exact selection mechanics remain replaceable local implementation details rather than additional specification requirements.
- Keep random pending picks as real, decided-ahead choices. Ordinary reads and restarts do not reroll them.
- Make the user-keyed playback context the consistency boundary for queue, transport, and progress, eliminating independently authoritative active-playlist and current-item copies.
- Distinguish explicit stop, which retains track selection, from natural exhaustion, which consumes the last occurrence and legitimately leaves no current item.
- Provide domain queue-management operations without exposing public queue-management endpoints in this change.
- **BREAKING** Make playlists and playback contexts user-owned, using the stable user identity resolved from username-based acquisition by `introduce-client-sessions`. Scope playlist reads, mutations, selection, and source reconciliation to that user. Tracks and music sources remain shared.
- At most one of a user's client sessions holds that context's active-player role. Other sessions of that user must explicitly take over; different users' player roles and playback state are independent.
- **BREAKING** Replace playback initialization and track-scoped position reporting with ordered, per-session playback writes (commands and execution observations) that always return the canonical playback snapshot. Server restart does not change playback state.
- Identify each player instance by its client session alone. Attribute observations to the originating instruction using that command's request sequence, scoped by client session, without another revision allocator.
- Use best-effort client/server synchronization: retries are optional, newer actions may supersede uncertain writes, and only applied writes need durable deduplication. Unapplied retries reevaluate operation-specific preconditions; intermediate actions are not guaranteed delivery.
- Require relevant canonical-state matches for commands and attached checkpoints. Superseded requests do not apply; clients reconcile from the snapshot. Session expiration removes player authority without changing playback state, and track retrieval requires session validity rather than the player role.
- Actual authority transfer, including claiming an unowned context, establishes one fresh instruction and Unconfirmed execution. Redundant takeover flags follow normal command/no-op rules.
- Treat duration as advisory: accept nonnegative representable positions without a duration ceiling or clamping; completion remains explicit.
- Expose a persisted ordering-only context revision in snapshots, never as a write precondition or instruction identifier.

The HLD, concrete Part 2 contracts, owned specification deltas, and Phase 2/3 tasks form a complete planning handoff for review. Implementation remains a separate workflow.

## Capabilities

### New Capabilities

- `playback-queue`: Durable playback occurrences, current/pending sequence ownership, retention policies, source continuation, and reconciliation with source changes.

### Modified Capabilities

- `playlist-management`: User-owned playlists, stable entry identities, strict reorder, explicit membership changes, and playlist-owned mode preference without playback-domain coupling.
- `playback-control`: Separate queue-backed context per user; one active player per context and explicit takeover; occurrence identity; atomic sequence/transport/progress transitions; distinct stop and natural-exhaustion behavior.
- `player-rest-api`: User-scoped playlists, dedicated typed playback command/observation routes, and canonical snapshots with 200 applied/retry and 409 not-applied/conflict responses, replacing `POST /api/playback/init`.
- `audio-streaming`: Remove byte-derived playback position; refresh all users' Play/Pause contexts with a current occurrence, bounded by actual current/pending choices and configured count, with shared-track deduplication at integration.
- `track-registry`: Missing-source cleanup protects shared tracks referenced by any user's playlist or playback queue, including detached occurrences.
- `observability-and-correlation`: Align operation lifecycle terminology with occurrence-based playback and playlist-only mode changes while preserving request correlation.
- `agent-first-project-documentation`: Document queue/context architecture, phase review gates, normal-use MockMvc acceptance, and revised playback correlation guidance.

## Impact

- Depends on `introduce-client-sessions` (Phase 1) for caller identity, session-required handling, user resolution, and the verified selective-reset baseline, and on `replace-jmolecules-with-vertical-slices` (Phase 1.5) for enforced vertical package slices, YAGNI concrete repositories, and multi-bean reference collection. Phase 1 owns removal of the obsolete subsystem; Phase 1.5 owns the structural slice cleanup; this change owns the playlist and playback replacement.
- Implements two reviewable phases after the session and vertical-slice foundations: Phase 2 builds user-owned playlists, queue/domain behavior, contained context persistence, and atomic event-driven source reconciliation; Phase 3 adds playback commands/observations, authority/sequencing, snapshots, and refresh integration. Green phase boundaries do not require end-to-end playback before Phase 3.
- Affects playlist and playback domain models, application services, concrete domain repositories, persistence entities, and related API DTOs/controllers and tests.
- Replaces dual active-playlist state and mutable playback singleton with one canonical context, lazy initialization under the shared user-row lock, and explicit context revision updates.
- Changes the initial database schema and entry/playback API shapes. There are no deployed databases, existing clients, or real data requiring compatibility or a migration path; edit the initial schema directly while retaining Hibernate validation.
- Requires registry cleanup to account for every user's playlist and queue references before treating shared tracks as unreferenced. Reference lookup must be restored in Phase 2 when those references become durable, not deferred to protocol integration.
- Restores scheduling through an upcoming-track adapter over real occurrences; duplicate occurrences remain distinct while shared-track refresh work is deduplicated at that boundary.
- No new dependencies have been selected.
- Client implementation is deliberately deferred until the API/workflow settle. The outer acceptance boundary is Spring Boot/MockMvc integration tests using real services/persistence for ordinary acquisition, playback, seek, handoff, playlist-edit, and takeover workflows; audible gaplessness/browser behavior is not claimed.
- Retains focused domain/application/persistence invariant tests, while deferring dedicated race orchestration, mid-flight network failures, and adversarial/rogue-intent integration scenarios to separate work.
- Does not maintain legacy routes, singleton state, dual writes, or compatibility migrations. Preserves useful retained components and established behavioral checks; phase-specific absences are explicit rather than hidden behind dummy services or weakened verification.
- Reflect completed architecture and conventions in related project documentation during the owning implementation phase.
- User scoping partitions state but does not authenticate ownership: anyone supplying a username can select that user's state.
- Outside this change: authentication, authenticated authorization, multiple simultaneous players for one playback context, remote-control clients that send commands without playing, public queue-management APIs, and their UI. Different users may play independently.
