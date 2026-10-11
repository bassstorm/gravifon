## MODIFIED Requirements

### Requirement: Correlation scope is tied to active operation lifecycle
Correlation SHALL scope one active operation/resource lifecycle. Playback occurrence lifecycles, playlist mutations and session acquisition SHALL remain distinguishable. Repeated shared tracks MUST NOT collapse separate playback occurrences into one lifecycle; request propagation rules remain unchanged.

#### Scenario: Playback continues within same track lifecycle
- **WHEN** pause/resume/seek/metadata/buffering events concern the same playback occurrence
- **THEN** related diagnostics retain that playback lifecycle correlation

#### Scenario: Lifecycle boundary is reached
- **WHEN** explicit selection, Next/Advance, or Stop followed by replay creates another occurrence
- **THEN** it starts a new playback lifecycle even for the same track ID

#### Scenario: Non-track operation starts a new lifecycle
- **WHEN** acquisition or playlist mutation, including preference change, is performed
- **THEN** it is a dedicated operation, not an implicit active-playlist switch or playback-mode command
