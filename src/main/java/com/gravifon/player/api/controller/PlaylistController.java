package com.gravifon.player.api.controller;

import com.gravifon.player.api.model.PlaybackStateResponse;
import com.gravifon.player.api.model.PlaylistMutationRequest;
import com.gravifon.player.api.model.PlaylistResponse;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.registry.service.StreamTrackRegistrar;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {
    private final PlaylistService playlistService;
    private final PlaybackService playbackService;
    private final StreamTrackRegistrar streamTrackRegistrar;

    @GetMapping
    public List<PlaylistResponse> listPlaylists() {
        String activeId = playlistService.activeId().orElse(null);
        return playlistService
            .listPlaylists()
            .stream()
            .map(playlist -> PlaylistResponse.from(playlist, activeId))
            .toList();
    }

    @GetMapping("/{playlistId}")
    public PlaylistResponse getPlaylist(@PathVariable String playlistId) {
        Playlist playlist = playlistService.getPlaylist(playlistId);
        return PlaylistResponse.from(playlist, playlistService.activeId().orElse(null));
    }

    @PostMapping("/{playlistId}/select")
    public PlaybackStateResponse selectPlaylist(@PathVariable String playlistId) {
        return PlaybackStateResponse.from(playbackService.selectPlaylist(playlistId));
    }

    @PostMapping
    @Transactional
    public PlaylistResponse createPlaylist(@RequestBody PlaylistMutationRequest request) {
        requireName(request.name());
        if (Boolean.TRUE.equals(request.catalog())) {
            if (!request.trackIds().isEmpty() || !request.sourceUrls().isEmpty()) {
                throw new IllegalArgumentException("Catalog materialization cannot include entries");
            }
            return PlaylistResponse.from(
                    playlistService.materializeCatalog(request.name()),
                    playlistService.activeId().orElse(null)
            );
        }
        List<String> trackIds = new ArrayList<>(request.trackIds());
        if (!request.sourceUrls().isEmpty()) {
            trackIds.addAll(streamTrackRegistrar
                .registerSources(request.sourceUrls())
                .stream()
                .map(track -> track.id())
                .toList()
            );
        }
        return PlaylistResponse.from(
                playlistService.create(request.name(), trackIds, request.mode()),
                playlistService.activeId().orElse(null)
        );
    }

    @Transactional
    @org.springframework.web.bind.annotation.PatchMapping("/{playlistId}")
    public PlaylistResponse updatePlaylist(
            @PathVariable String playlistId,
            @RequestBody PlaylistMutationRequest request
    ) {
        Playlist playlist = playlistService.getPlaylist(playlistId);
        if (request.name() != null) {
            playlist = playlistService.rename(playlistId, request.name());
        }
        if (!request.reorder().isEmpty()) {
            playlist = playlistService.reorder(playlistId, request.reorder());
        }
        List<String> additions = new ArrayList<>(request.add());
        if (!request.sourceUrls().isEmpty()) {
            additions.addAll(streamTrackRegistrar
                .registerSources(request.sourceUrls())
                .stream()
                .map(track -> track.id())
                .toList()
            );
        }
        if (!additions.isEmpty()) {
            playlist = playlistService.addEntries(playlistId, additions);
        }
        if (!request.remove().isEmpty()) {
            playlist = playlistService.removeEntries(playlistId, request.remove());
        }
        if (request.mode() != null) {
            playlist = playlistService.setMode(playlistId, request.mode());
        }
        return PlaylistResponse.from(playlist, playlistService.activeId().orElse(null));
    }

    @DeleteMapping("/{playlistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlaylist(@PathVariable String playlistId) {
        playlistService.delete(playlistId);
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Playlist name is required");
        }
    }
}
