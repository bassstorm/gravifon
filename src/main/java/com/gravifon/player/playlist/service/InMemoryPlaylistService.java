package com.gravifon.player.playlist.service;

import com.gravifon.player.api.error.ResourceNotFoundException;
import com.gravifon.player.catalog.service.MediaCatalogService;
import com.gravifon.player.playlist.model.Playlist;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class InMemoryPlaylistService {

    public static final String DEFAULT_PLAYLIST_ID = "all-tracks";
    public static final String DEFAULT_PLAYLIST_NAME = "All Tracks";

    private final MediaCatalogService mediaCatalogService;
    private final Map<String, Playlist> playlists = new ConcurrentHashMap<>();
    private final AtomicReference<String> activePlaylistId = new AtomicReference<>();

    public InMemoryPlaylistService(MediaCatalogService mediaCatalogService) {
        this.mediaCatalogService = mediaCatalogService;
    }

    @PostConstruct
    public void rebuildOnStartup() {
        rebuildFromCatalog();
    }

    public synchronized void rebuildFromCatalog() {
        playlists.clear();
        List<String> allTrackIds = mediaCatalogService.listTracks().stream().map(track -> track.id()).toList();
        Playlist defaultPlaylist = new Playlist(DEFAULT_PLAYLIST_ID, DEFAULT_PLAYLIST_NAME, allTrackIds);
        playlists.put(defaultPlaylist.id(), defaultPlaylist);
        activePlaylistId.set(defaultPlaylist.id());
    }

    public List<Playlist> listPlaylists() {
        return List.copyOf(playlists.values());
    }

    public Playlist getPlaylist(String playlistId) {
        return Optional.ofNullable(playlists.get(playlistId))
                .orElseThrow(() -> new ResourceNotFoundException("Playlist not found: " + playlistId));
    }

    public Playlist selectPlaylist(String playlistId) {
        Playlist playlist = getPlaylist(playlistId);
        activePlaylistId.set(playlist.id());
        return playlist;
    }

    public Playlist getActivePlaylist() {
        String id = activePlaylistId.get();
        if (id == null) {
            throw new ResourceNotFoundException("No active playlist selected");
        }
        return getPlaylist(id);
    }
}

