package com.gravifon.player.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "playlist")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PlaylistEntity {
    @Id
    private String id;
    private String name;
    private String playbackMode;
    @Column(name = "created_at")
    private long createdAt;
    @Column(name = "updated_at")
    private long updatedAt;
    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PlaylistEntryEntity> entries = new ArrayList<>();

    public PlaylistEntity(String id, String name, String playbackMode, Instant now) {
        this.id = id;
        this.name = name;
        this.playbackMode = playbackMode;
        this.createdAt = now.getEpochSecond();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String playbackMode, Instant now) {
        this.name = name;
        this.playbackMode = playbackMode;
        this.updatedAt = now.getEpochSecond();
    }

    public void replaceEntries(List<PlaylistEntryEntity> values) {
        entries.clear();
        entries.addAll(values);
        values.forEach(value -> value.attach(this));
    }
}
