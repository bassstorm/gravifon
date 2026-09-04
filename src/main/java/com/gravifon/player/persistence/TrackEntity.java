package com.gravifon.player.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "track")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TrackEntity {
    @Id
    private String id;

    private String kind;

    @Column(name = "duration_s")
    private Long durationSeconds;

    @Column(name = "rel_path")
    private String relativePath;

    private String format;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "stream_url")
    private String streamUrl;

    @Column(name = "expires_after")
    private Long expiresAfter;

    private boolean failing;

    @Column(name = "error_kind")
    private String errorKind;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "error_at")
    private Long errorAt;

    @Column(name = "error_reporter")
    private String errorReporter;

    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TrackMetadataEntity> metadata = new ArrayList<>();

    public TrackEntity(
            String id,
            String kind,
            Long durationSeconds,
            String relativePath,
            String format,
            String sourceUrl,
            String streamUrl,
            Long expiresAfter,
            boolean failing,
            String errorKind,
            String errorMessage,
            Long errorAt,
            String errorReporter) {
        this.id = id;
        this.kind = kind;
        this.durationSeconds = durationSeconds;
        this.relativePath = relativePath;
        this.format = format;
        this.sourceUrl = sourceUrl;
        this.streamUrl = streamUrl;
        this.expiresAfter = expiresAfter;
        this.failing = failing;
        this.errorKind = errorKind;
        this.errorMessage = errorMessage;
        this.errorAt = errorAt;
        this.errorReporter = errorReporter;
    }

    public void replaceMetadata(List<TrackMetadataEntity> values) {
        metadata.clear();
        metadata.addAll(values);
        values.forEach(value -> value.attach(this));
    }
}
