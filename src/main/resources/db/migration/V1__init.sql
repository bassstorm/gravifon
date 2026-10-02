CREATE TABLE track (
    id VARCHAR(255) PRIMARY KEY,
    kind VARCHAR(255) NOT NULL,
    duration_s BIGINT,
    rel_path VARCHAR(255),
    format VARCHAR(255),
    source_url VARCHAR(255),
    stream_url VARCHAR(255),
    expires_after BIGINT,
    failing BOOLEAN NOT NULL DEFAULT FALSE,
    error_kind VARCHAR(255),
    error_message VARCHAR(255),
    error_at BIGINT,
    error_reporter VARCHAR(255)
);

CREATE TABLE track_metadata (
    track_id VARCHAR(255) NOT NULL REFERENCES track(id) ON DELETE CASCADE,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value VARCHAR(255) NOT NULL,
    ord INTEGER NOT NULL,
    PRIMARY KEY (track_id, metadata_key, ord)
);

CREATE TABLE playlist (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    playback_mode VARCHAR(255) NOT NULL DEFAULT 'SEQUENTIAL',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE playlist_entry (
    playlist_id VARCHAR(255) NOT NULL REFERENCES playlist(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    track_id VARCHAR(255) NOT NULL REFERENCES track(id),
    PRIMARY KEY (playlist_id, position)
);

CREATE TABLE playback_state (
    session_id VARCHAR(255) PRIMARY KEY,
    active_playlist_id VARCHAR(255) REFERENCES playlist(id),
    current_track_id VARCHAR(255) REFERENCES track(id),
    transport_state VARCHAR(255) NOT NULL,
    position_seconds BIGINT NOT NULL DEFAULT 0,
    position_origin VARCHAR(255),
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_track_stream_expires_after ON track(kind, expires_after);