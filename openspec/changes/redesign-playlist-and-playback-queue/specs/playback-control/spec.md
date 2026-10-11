## MODIFIED Requirements

### Requirement: Playback context is server-maintained
The system SHALL maintain one canonical durable context per user containing queue, intent, bookmark, active session, instruction, execution evidence and revision. Queue/current/progress transitions SHALL commit atomically without independently authoritative memory copies.

#### Scenario: Multiple clients read player state
- **WHEN** two sessions of one user read playback
- **THEN** both receive the same canonical context facts with caller-specific authority flags

#### Scenario: Transport state is server-maintained
- **WHEN** Play is requested but execution reports Buffering or Failed
- **THEN** both desired intent and observed execution remain distinguishable

#### Scenario: Stopped transport resets position
- **WHEN** Stop applies
- **THEN** selection is retained, bookmark becomes zero, and pending clears

#### Scenario: Playback context survives client reconnects
- **WHEN** a new valid session of that user reads playback
- **THEN** persisted state is visible without acquisition automatically taking over

#### Scenario: Playback context survives server restart
- **WHEN** the server restarts
- **THEN** queue choices, continuation, intent, bookmark, valid authority, instruction, applied dedup and observations survive without normalization

### Requirement: Playback modes include sequential and random
Playback SHALL interpret the bound playlist's sequential/random preference through an encapsulated source policy. Reads/restart and retained promotion MUST NOT reroll valid generated choices; source changes SHALL invoke policy reconciliation.

#### Scenario: Sequential mode advances by order
- **WHEN** sequential source selection is needed
- **THEN** the policy uses entry order and continuation rather than track-ID lookup

#### Scenario: Random mode advances by random choice
- **WHEN** random source selection is needed
- **THEN** actual random choices are persisted ahead of playback

#### Scenario: Random mode does not imply shuffle cycle
- **WHEN** the initial repetition/wrapping/replacement mechanics are changed in a focused policy update
- **THEN** queue callers and authority protocol need no separate repair or selection logic

### Requirement: Playback context commands are entity-reference driven
Playback SHALL support entry-addressed explicit play and occurrence/instruction-sensitive Play, Pause, Stop, Next, Retry and Advance commands. Operations MUST validate relevant expected state and attached checkpoints before applying any mutation.

#### Scenario: Client selects an explicit track
- **WHEN** a caller selects an owned playlist entry
- **THEN** it becomes a new current occurrence at zero/Play with that source bound, retained pending preserved and generated choices reconciled

#### Scenario: Prepare track context when playback starts
- **WHEN** a playback read or Play without current occurs
- **THEN** reads do not invent current and unsupported Play fails explicitly

#### Scenario: Next-track selection is server-driven
- **WHEN** Next applies
- **THEN** ordinary queue advancement selects at zero preserving intent, or exhausts to empty/Stop

#### Scenario: Retry failed execution
- **WHEN** Retry applies to the current failed instruction
- **THEN** occurrence/bookmark/pending remain, a fresh instruction is created, and no automatic skip occurs

#### Scenario: Superseded checkpoint
- **WHEN** command or attached checkpoint references superseded playback
- **THEN** the entire command conflicts with canonical snapshot and no partial mutation

## REMOVED Requirements

### Requirement: Switching active playlist or mode eagerly resets current track selection
**Reason**: Browsing is not source binding and mode belongs to playlist mutation.
**Migration**: Use explicit play-from-entry to replace selection and playlist mutation to change preference.

### Requirement: Playback position is tracked with an optional client report and a passive fallback
**Reason**: Byte delivery cannot establish playback progress or plausibility.
**Migration**: Use occurrence/instruction-attributed execution observations with explicit position.

### Requirement: Playing transport state is normalized when no player is attached
**Reason**: Restart/inactivity is not a transport command or evidence that audio stopped.
**Migration**: Preserve intent/evidence; only actual deliberate takeover normalizes Play to Pause.

### Requirement: Playback context is session-keyed
**Reason**: Users own durable state; sessions identify player instances.
**Migration**: Resolve the session user and access that user's context.

## ADDED Requirements

### Requirement: Users own independent playback contexts
Each stable user SHALL own one context, initially empty/Stop/zero. Same-user sessions share it; different users SHALL remain independent while referencing shared tracks. No active player SHALL exist until deliberate claim.

#### Scenario: Different users
- **WHEN** two users play the same registry track
- **THEN** their occurrence/queue/intent/bookmark/authority state remains independent

### Requirement: Takeover establishes one new instruction
Only the active session SHALL write playback unless a valid command explicitly transfers authority. Actual takeover, including unowned context claim, SHALL normalize Play to Pause then apply the command with one fresh instruction and Unconfirmed execution.

#### Scenario: No-op transport with transfer
- **WHEN** a different session takes over using a command whose transport effect is otherwise a no-op
- **THEN** a new instruction attributes execution to the new session

#### Scenario: Redundant flag
- **WHEN** the active session repeats takeover intent
- **THEN** ordinary command/no-op rules apply without a transfer-only instruction

### Requirement: Applied writes are durably ordered and deduplicated
Each playback write SHALL carry a positive session sequence. Higher sequences evaluate applicability; equal sequence/same canonical content returns Already applied; equal/different content is invalid; lower is Not applied. Only applied writes SHALL persist sequence/digest atomically with state.

#### Scenario: Retry before takeover evaluation
- **WHEN** an already-applied command is retried after the session was displaced
- **THEN** it returns Already applied/current snapshot without reclaiming authority or executing twice

#### Scenario: Unapplied retry
- **WHEN** an unanswered unapplied request is retried
- **THEN** current preconditions are reevaluated without a stored rejection cache

#### Scenario: Newer sequence supersedes uncertainty
- **WHEN** a higher write applies before an older uncertain write
- **THEN** the older request is stale and intermediate delivery is not guaranteed

### Requirement: Observations identify their originating instruction
Execution/position observations SHALL carry current occurrence and originating session/sequence instruction separately from their write sequence. Superseded reports MUST NOT mutate state. New instructions SHALL reset execution to Unconfirmed and clear prior failure.

#### Scenario: Repeated track with stale callback
- **WHEN** an old callback reports the same track under a newer sequence but stale occurrence/instruction
- **THEN** it is Not applied without updating state or dedup

### Requirement: Canonical position is explicit and duration advisory
The system SHALL accept nonnegative representable integer-millisecond positions from applicable observations, including seek/reset discontinuities and positions above registry duration. It MUST NOT clamp, extrapolate, infer completion, or derive progress from bytes.

#### Scenario: Seek and over-duration bookmark
- **WHEN** an applicable observation reports a seek or a position above metadata duration
- **THEN** that exact position persists without triggering Advance

#### Scenario: Invalid position
- **WHEN** an observation/checkpoint contains negative or unrepresentable position
- **THEN** the whole request fails without partial mutation or dedup update

### Requirement: Advance confirms actual queue successor
Advance SHALL validate completed occurrence/instruction and exact expected successor, including absence for exhaustion, then atomically consume/promote and establish a fresh instruction. No preparation resource or reservation SHALL be required.

#### Scenario: Normal handoff
- **WHEN** the current instruction completes and the expected persisted successor matches
- **THEN** Advance promotes it at zero/Play and successor reporting can use the new instruction

#### Scenario: No successor
- **WHEN** the expected and actual successor are absent
- **THEN** Advance consumes current and leaves empty/Stop/zero

#### Scenario: Successor changed
- **WHEN** a source edit changed the expected successor
- **THEN** Advance conflicts and returns canonical snapshot without invalidating cached track bytes

### Requirement: Snapshot revision orders committed context state
Snapshots SHALL expose a persisted server context revision incremented for committed context changes, including contained queue changes and authority clearing. It MUST NOT be a command precondition or instruction identifier. Reads/retries alone SHALL not increment it.

#### Scenario: Playlist reconciliation changes queue
- **WHEN** a playlist edit changes persisted queue facts
- **THEN** the next snapshot has a greater context revision

#### Scenario: Read or no-op retry
- **WHEN** canonical state is unchanged
- **THEN** reads and recognized retries retain its revision

### Requirement: Authority loss preserves playback facts
Expiration/invalidation SHALL clear only the matching stored active-session reference. Queue, intent, bookmark, instruction and last observation MUST remain unchanged. Another valid same-user session SHALL require deliberate takeover.

#### Scenario: Active session invalidated
- **WHEN** it is invalidated or expiration is detected
- **THEN** snapshots show no active player without Pause/Stop or queue changes

### Requirement: Execution failures do not diagnose shared tracks automatically
Applicable Failed observations SHALL retain occurrence/bookmark/intent/pending and structured instruction-scoped diagnostics. Shared track failure reports and scan/refresh diagnosis SHALL remain separate.

#### Scenario: Device decoder failure
- **WHEN** the current instruction reports a decoder failure
- **THEN** playback evidence is Failed without automatic skip or shared-track failure mutation
