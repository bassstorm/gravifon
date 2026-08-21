## MODIFIED Requirements

### Requirement: Playback modes include sequential and random
The system MUST support `sequential` and `random` playback modes that influence next-track selection.

#### Scenario: Sequential mode advances by order
- **WHEN** playback mode is sequential and next track is requested
- **THEN** the following track in playlist order is selected

#### Scenario: Random mode advances by random choice
- **WHEN** playback mode is random and next track is requested
- **THEN** the next track is chosen using random selection from the active playlist

#### Scenario: Random mode does not imply shuffle cycle
- **WHEN** playback mode is random
- **THEN** selection is pure random per step and SHALL NOT enforce shuffle-without-repeat behavior
