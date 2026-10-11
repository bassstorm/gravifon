## ADDED Requirements

### Requirement: Queue and playback implementation guidance is self-contained
Repository guidance SHALL describe user-owned context/queue boundaries, occurrence identity, retained source-independent components, phase review gates, and normal-use server API acceptance. It MUST distinguish server delivery from deferred client implementation.

#### Scenario: Implementor follows redesign guidance
- **WHEN** an implementor reads design/tasks and contribution docs
- **THEN** concrete phase prerequisites, contracts and behavioral verification do not depend on conversation history

### Requirement: Playback correlation guidance uses occurrence vocabulary
Guidance SHALL distinguish occurrence playback lifecycles, playlist preference mutations and session acquisition while preserving request correlation and severity conventions.

#### Scenario: Stop and replay
- **WHEN** documentation explains replay of the same track
- **THEN** it identifies a new occurrence/lifecycle rather than reusing track-only identity
