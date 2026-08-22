package com.gravifon.player.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "playback_state")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PlaybackStateEntity {
    @Id private String sessionId;
    private String activePlaylistId;
    private String currentTrackId;
    private String transportState;
    private long positionSeconds;
    private String positionOrigin;
    private long updatedAt;
    public PlaybackStateEntity(String sessionId, String activePlaylistId, String currentTrackId, String transportState,
                               long positionSeconds, String positionOrigin, long updatedAt) {
        this.sessionId = sessionId; this.activePlaylistId = activePlaylistId; this.currentTrackId = currentTrackId;
        this.transportState = transportState; this.positionSeconds = positionSeconds; this.positionOrigin = positionOrigin;
        this.updatedAt = updatedAt;
    }
}
