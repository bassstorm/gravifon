package com.gravifon.player.api.model;

import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TrackResponse(
        String id,
        String filename,
        TrackKind kind,
        Map<String, List<String>> metadata,
        String format,
        Long durationSeconds,
        TrackState state,
        String sourceUrl,
        Instant expiresAfter
) {
    public static TrackResponse from(Track track) {
        return switch (track) {
            case FileTrack file -> {
                String filename = file.relPath().getFileName().toString();
                yield new TrackResponse(
                        file.id(),
                        filename,
                        file.kind(),
                        file.metadata().values(),
                        file.format(),
                        file.durationSeconds(),
                        file.state(),
                        null,
                        null
                );
            }
            case StreamTrack stream -> new TrackResponse(
                    stream.id(),
                    null,
                    stream.kind(),
                    stream.metadata().values(),
                    null,
                    stream.durationSeconds(),
                    stream.state(),
                    stream.sourceUrl().toString(),
                    stream.expiresAfter()
            );
        };
    }
}
