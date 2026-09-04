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
@Table(name = "track_metadata")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TrackMetadataEntity {
    @EmbeddedId
    @AttributeOverrides({
        @AttributeOverride(name = "trackId", column = @Column(name = "track_id")),
        @AttributeOverride(name = "key", column = @Column(name = "key")),
        @AttributeOverride(name = "order", column = @Column(name = "ord"))
    })
    private TrackMetadataId id;

    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", insertable = false, updatable = false)
    private TrackEntity track;

    public TrackMetadataEntity(String key, String value, int order) {
        this.id = new TrackMetadataId(null, key, order);
        this.value = value;
    }

    public String key() {
        return id.key();
    }

    public int order() {
        return id.order();
    }

    void attach(TrackEntity owner) {
        this.track = owner;
        this.id = new TrackMetadataId(owner.id(), id.key(), id.order());
    }

    public record TrackMetadataId(String trackId, String key, int order) implements Serializable {}
}
