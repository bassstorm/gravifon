package com.gravifon.player.registry.stream;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.repository.TrackRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StreamRefreshScheduler {

    private final PlaylistService playlistService;
    private final TrackRepository trackRepository;
    private final StreamRefreshService refreshService;
    private final PlaybackService playbackService;
    private final GravifonProperties properties;
    private final Clock clock;

    @Autowired
    public StreamRefreshScheduler(PlaylistService playlistService, TrackRepository trackRepository,
                                  StreamRefreshService refreshService, GravifonProperties properties,
                                  PlaybackService playbackService) {
        this(playlistService, trackRepository, refreshService, properties, playbackService, Clock.systemUTC());
    }

    StreamRefreshScheduler(PlaylistService playlistService, TrackRepository trackRepository,
                           StreamRefreshService refreshService, GravifonProperties properties,
                           PlaybackService playbackService, Clock clock) {
        this.playlistService = playlistService;
        this.trackRepository = trackRepository;
        this.refreshService = refreshService;
        this.properties = properties;
        this.playbackService = playbackService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gravifon.streams.refresh-interval:PT10M}", timeUnit = TimeUnit.MILLISECONDS)
    public void refreshUpcomingStreams() {
        if (properties.getStreams().isRefreshEnabled()) {
            refreshScope();
        }
    }

    void refreshScope() {
        Playlist active;
        try {
            active = playlistService.getActive();
        } catch (RuntimeException ignored) {
            return;
        }
        Instant threshold = clock.instant().plus(properties.getStreams().getRefreshAhead());
        Set<String> scope = new HashSet<>();
        if ("ACTIVE_PLAYLIST_PLUS_NEXT".equalsIgnoreCase(properties.getStreams().getRefreshScope())) {
            addPlaybackScope(scope, active);
        } else {
            scope.addAll(active.trackIds());
        }
        for (String trackId : scope) {
            trackRepository.findById(trackId)
                    .filter(track -> track.kind() == TrackKind.STREAM)
                    .filter(track -> track.expiresAfter() == null || !track.expiresAfter().isAfter(threshold))
                    .ifPresent(this::refresh);
        }
    }

    private void addPlaybackScope(Set<String> scope, Playlist active) {
        if (playbackService == null) {
            return;
        }
        PlaybackState state;
        try {
            state = playbackService.getState();
        } catch (RuntimeException ignored) {
            return;
        }
        if (state.currentTrackId() == null || state.playbackMode() != PlaybackMode.SEQUENTIAL) {
            return;
        }
        int currentIndex = active.trackIds().indexOf(state.currentTrackId());
        if (currentIndex >= 0 && !active.trackIds().isEmpty()) {
            scope.add(state.currentTrackId());
            scope.add(active.trackIds().get((currentIndex + 1) % active.trackIds().size()));
        }
    }

    private void refresh(Track track) {
        try {
            refreshService.ensureFresh(track);
        } catch (IOException ignored) {
            // Request-time fallback retries after a bounded scheduler attempt.
        }
    }
}
