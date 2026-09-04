package com.gravifon.player.persistence;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "playlist_entry")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PlaylistEntryEntity {
    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "playlistId", column = @Column(name = "playlist_id")),
            @AttributeOverride(name = "position", column = @Column(name = "position")),
            @AttributeOverride(name = "trackId", column = @Column(name = "track_id"))
    })
    private PlaylistEntryId id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", insertable = false, updatable = false)
    private PlaylistEntity playlist;

    public PlaylistEntryEntity(int position, String trackId) {
        this.id = new PlaylistEntryId(null, position, trackId);
    }

    public int position() {
        return id.position();
    }

    public String trackId() {
        return id.trackId();
    }

    void attach(PlaylistEntity owner) {
        playlist = owner;
        id = new PlaylistEntryId(owner.id(), id.position(), id.trackId());
    }

    public record PlaylistEntryId(String playlistId, int position, String trackId) implements Serializable {}
}
