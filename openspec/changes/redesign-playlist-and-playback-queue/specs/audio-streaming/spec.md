## MODIFIED Requirements

### Requirement: Stream URL refresh is performed preemptively for upcoming playback
Scheduled refresh SHALL use all users' Play/Pause contexts with current occurrence, taking current then actual pending up to configured count, default ten, bounded by available choices. Shared-track work SHALL be deduplicated. Look-ahead and cron remain independent; no startup run is required, `-` disables scheduling, and missed coverage uses request-time refresh.

#### Scenario: Upcoming track is refreshed before expiry
- **WHEN** an eligible actual stream occurrence approaches the look-ahead boundary
- **THEN** its URL is refreshed and persisted

#### Scenario: Out-of-scope tracks are not refreshed
- **WHEN** a track is outside eligible contexts or beyond per-context count
- **THEN** it is not proactively refreshed and remains eligible for request-time refresh

#### Scenario: Proactive refresh failure does not mark a still-valid track
- **WHEN** refresh fails with an unexpired URL
- **THEN** existing failure policy preserves still-usable state and later schedule retries

#### Scenario: Scheduler behavior is configurable
- **WHEN** operator changes look-ahead, cron, count or minimum-validity settings
- **THEN** subsequent scheduled executions use that configuration without code changes

#### Scenario: Proactive refresh can be disabled
- **WHEN** cron is `-`
- **THEN** no proactive runs occur and request-time refresh remains

#### Scenario: Missed proactive refresh falls back to request time
- **WHEN** a URL needs refresh before scheduling reaches it
- **THEN** the audio request refreshes it before serving

#### Scenario: Default horizon does not invent coverage
- **WHEN** current and one generated successor are the only actual choices
- **THEN** at most those two are considered despite default count ten

#### Scenario: Shared tracks across users
- **WHEN** multiple eligible occurrences reference one track
- **THEN** queue occurrences stay distinct but scheduled refresh work uses that shared track once

### Requirement: Streaming remains free of authoritative playback-state side effects
Audio retrieval SHALL never establish position/execution, select/advance, reroll or acquire playback authority. Session validation/renewal remains required but fetching bytes is not playback evidence.

#### Scenario: Streaming a non-current track has no effect on playback state
- **WHEN** a valid session prefetches a non-current track
- **THEN** queue, intent, bookmark and execution remain unchanged

#### Scenario: Streaming activity never overrides an explicit, plausible position report
- **WHEN** current audio is fetched or cached after an observation
- **THEN** neither explicit bookmark nor execution changes from byte delivery
