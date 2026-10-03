package com.gravifon.player.api.controller;

import com.gravifon.player.api.model.PlaybackStateResponse;
import com.gravifon.player.api.model.PlaylistCreateRequest;
import com.gravifon.player.api.model.PlaylistResponse;
import com.gravifon.player.api.model.PlaylistUpdateRequest;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.PlaylistMutation;
import com.gravifon.player.playlist.service.PlaylistService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public PlaylistResponse createPlaylist(@Valid @RequestBody PlaylistCreateRequest request) {
        return PlaylistResponse.from(
                playlistService.createFromRequest(
                        new PlaylistMutation(
                                request.name(),
                                request.trackIds(),
                                request.sourceUrls(),
                                request.catalog(),
                                List.of(),
                                List.of(),
                                List.of(),
                                request.mode()
                        )
                ),
                playlistService.activeId().orElse(null)
        );
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{playlistId}")
    public PlaylistResponse updatePlaylist(
            @PathVariable String playlistId,
            @Valid @RequestBody PlaylistUpdateRequest request
    ) {
        Playlist playlist = playlistService.updateFromRequest(
                playlistId,
                new PlaylistMutation(
                        request.name(),
                        List.of(),
                        List.of(),
                        null,
                        request.reorder(),
                        request.add(),
                        request.remove(),
                        request.mode()
                )
        );
        return PlaylistResponse.from(playlist, playlistService.activeId().orElse(null));
    }

    @DeleteMapping("/{playlistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlaylist(@PathVariable String playlistId) {
        playlistService.delete(playlistId);
    }
}
