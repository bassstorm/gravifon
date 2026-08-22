CREATE TABLE track (
    id TEXT PRIMARY KEY,
    kind TEXT NOT NULL,
    duration_s INTEGER,
    rel_path TEXT,
    format TEXT,
    source_url TEXT,
    stream_url TEXT,
    expires_after INTEGER,
    failing INTEGER NOT NULL DEFAULT 0,
    error_kind TEXT,
    error_message TEXT,
    error_at INTEGER,
    error_reporter TEXT
);

CREATE TABLE track_metadata (
    track_id TEXT NOT NULL REFERENCES track(id) ON DELETE CASCADE,
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    ord INTEGER NOT NULL,
    PRIMARY KEY (track_id, key, ord)
);

CREATE TABLE playlist (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    playback_mode TEXT NOT NULL DEFAULT 'SEQUENTIAL',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE playlist_entry (
    playlist_id TEXT NOT NULL REFERENCES playlist(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    track_id TEXT NOT NULL REFERENCES track(id),
    PRIMARY KEY (playlist_id, position)
);

CREATE TABLE playback_state (
    session_id TEXT PRIMARY KEY,
    active_playlist_id TEXT REFERENCES playlist(id),
    current_track_id TEXT REFERENCES track(id),
    transport_state TEXT NOT NULL,
    position_seconds INTEGER NOT NULL DEFAULT 0,
    position_origin TEXT,
    updated_at INTEGER NOT NULL
);

CREATE INDEX idx_track_expires_after ON track(expires_after)
    WHERE kind = 'STREAM' AND expires_after IS NOT NULL;