package com.gravifon.player.api.controller;

import com.gravifon.player.api.model.PlaylistResponse;
import com.gravifon.player.api.model.PlaybackStateResponse;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.playlist.service.InMemoryPlaylistService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final InMemoryPlaylistService playlistService;
    private final PlaybackService playbackService;

    public PlaylistController(InMemoryPlaylistService playlistService, PlaybackService playbackService) {
        this.playlistService = playlistService;
        this.playbackService = playbackService;
    }

    @GetMapping
    public List<PlaylistResponse> listPlaylists() {
        String activeId = playlistService.getActivePlaylist().id();
        return playlistService.listPlaylists().stream()
                .map(playlist -> PlaylistResponse.from(playlist, activeId))
                .toList();
    }

    @GetMapping("/{playlistId}")
    public PlaylistResponse getPlaylist(@PathVariable String playlistId) {
        Playlist playlist = playlistService.getPlaylist(playlistId);
        return PlaylistResponse.from(playlist, playlistService.getActivePlaylist().id());
    }

    @PostMapping("/{playlistId}/select")
    public PlaybackStateResponse selectPlaylist(@PathVariable String playlistId) {
        return PlaybackStateResponse.from(playbackService.selectPlaylist(playlistId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public void createPlaylist(@RequestBody Object ignored) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Playlist mutations are not yet implemented");
    }

    @PutMapping("/{playlistId}")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public void updatePlaylist(@PathVariable String playlistId, @RequestBody Object ignored) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Playlist mutations are not yet implemented");
    }

    @DeleteMapping("/{playlistId}")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public void deletePlaylist(@PathVariable String playlistId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Playlist mutations are not yet implemented");
    }
}

