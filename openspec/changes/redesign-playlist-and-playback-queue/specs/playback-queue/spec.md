## Purpose

Define durable playback occurrences, ordered current and pending segments, and automatic source reconciliation within one playback context.

## ADDED Requirements

### Requirement: Queue occurrences are independent identities
Each queue occurrence SHALL have its own ID distinct from track and playlist-entry IDs, a shared track reference, optional playlist/entry association, and placement-consistent policy. Current SHALL contain at most one occurrence.

#### Scenario: Repeated track
- **WHEN** two occurrences reference the same track or entry
- **THEN** they remain independently addressable through consumption and reporting

#### Scenario: Detached item
- **WHEN** an occurrence loses its playlist-entry association
- **THEN** its occurrence ID and track reference remain valid

### Requirement: Retained pending precedes generated pending
Pending SHALL consist of retained items followed by source-managed items, preserving retained relative order. Promotion SHALL change the selected pending item's policy to current.

#### Scenario: Retained promotion
- **WHEN** retained and generated pending items exist and advancement occurs
- **THEN** the first retained item is promoted without repositioning source continuation or rerolling valid generated choices

### Requirement: Generated choices are persisted and bounded
The generated segment SHALL contain actual persisted choices up to its configured horizon, default one. Reads/restarts MUST NOT reroll choices. Every segment, including the entire queue, SHALL support empty state without fabricated fallback selections.

#### Scenario: Horizon exceeds one
- **WHEN** the horizon is configured above one
- **THEN** persisted generated occurrences preserve order and distinct identity across reads and reload

#### Scenario: Empty policy output
- **WHEN** source reconciliation produces no generated choices
- **THEN** the queue handles that empty output using ordinary retained/advancement/exhaustion rules

### Requirement: Semantic queue operations reconcile automatically
Source-managed removal, advancement, pending clearing, and source changes SHALL invoke encapsulated horizon reconciliation within the operation. Callers MUST NOT require a second repair step. Policy mechanics remain replaceable behind this contract.

#### Scenario: Generated removal
- **WHEN** a generated occurrence is removed
- **THEN** the same operation reconciles its segment without an exclusion-list promise or caller repair

#### Scenario: Pending clear
- **WHEN** pending is cleared during Play or Pause
- **THEN** retained pending is removed and generated contribution reconciles while preserving current and continuation

### Requirement: Source edits protect current and retained occurrences
Entry removal, order/membership/preference edits SHALL preserve current occurrence/progress and retained relative order, detach removed associations, and reconcile source-managed choices atomically. Stop SHALL still reconcile source facts without populating pending.

#### Scenario: Remove repeated entry
- **WHEN** one of two entries sharing a track is removed
- **THEN** only associations to that entry detach and current/retained occurrence IDs survive

#### Scenario: Delete backing playlist
- **WHEN** the backing playlist is deleted
- **THEN** current/retained items survive detached, generated choices and binding/continuation clear, and current playback is not stopped

### Requirement: Detached continuation survives source changes
Sequential continuation after detachment SHALL use a stable next-surviving-entry anchor, preserved across reorder and pending rebuild. Removed anchors SHALL advance through pre-edit order; no survivor means no anchor.

#### Scenario: Detached anchor reordered
- **WHEN** current entry B is removed with C as its next survivor and the playlist is reordered
- **THEN** C remains the continuation anchor, then selection follows resulting source order

#### Scenario: Empty source becomes populated
- **WHEN** a bound empty source gains entries without a surviving anchor
- **THEN** continuation initializes without automatically starting stopped playback

### Requirement: Stop differs from queue exhaustion
Explicit Stop SHALL retain selected track at zero and clear all pending, including retained items, without replenishment. Exhaustion SHALL consume current and leave absent current/Stop/zero. Replay after Stop SHALL create a new occurrence.

#### Scenario: Stop and replay
- **WHEN** playback is explicitly stopped then played
- **THEN** the same track selection replays at zero with a new occurrence and replenished pending choices

#### Scenario: Consume without successor
- **WHEN** advancement consumes current and no successor exists
- **THEN** current becomes absent, intent Stop, and position zero
