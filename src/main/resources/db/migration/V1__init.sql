CREATE TABLE track (
    id VARCHAR(255) NOT NULL,
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
    error_reporter VARCHAR(255),
    CONSTRAINT pk_track PRIMARY KEY (id)
);

CREATE TABLE track_metadata (
    track_id VARCHAR(255) NOT NULL REFERENCES track(id) ON DELETE CASCADE,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value VARCHAR(255) NOT NULL,
    ord INTEGER NOT NULL,
    PRIMARY KEY (track_id, metadata_key, ord)
);

CREATE TABLE app_user (
    id VARCHAR(36) PRIMARY KEY,
    normalized_name VARCHAR(64) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_app_user_normalized_name UNIQUE (normalized_name)
);

CREATE TABLE admission_lock (
    scope VARCHAR(32) PRIMARY KEY
);

INSERT INTO admission_lock (scope) VALUES ('USER'), ('TRACK');

CREATE TABLE client_session (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invalidated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_client_session_user ON client_session(user_id);
CREATE INDEX idx_client_session_activity ON client_session(last_activity_at);
CREATE INDEX idx_track_stream_expires_after ON track(kind, expires_after);