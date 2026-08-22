package com.gravifon.player.playlist.service;

import com.gravifon.player.api.error.ResourceNotFoundException;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.repository.PlaylistRepository;
import com.gravifon.player.registry.repository.TrackRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;
    private final AtomicReference<String> activePlaylistId = new AtomicReference<>();

    public PlaylistService(PlaylistRepository playlistRepository, TrackRepository trackRepository) {
        this.playlistRepository = playlistRepository;
        this.trackRepository = trackRepository;
    }

    public List<Playlist> listPlaylists() {
        return playlistRepository.findAll();
    }

    public Playlist getPlaylist(String playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist not found: " + playlistId));
    }

    public Playlist select(String playlistId) {
        Playlist playlist = getPlaylist(playlistId);
        activePlaylistId.set(playlist.id());
        return playlist;
    }

    public void activate(String playlistId) {
        activePlaylistId.set(getPlaylist(playlistId).id());
    }

    public Playlist getActive() {
        String playlistId = activePlaylistId.get();
        if (playlistId == null) {
            throw new ResourceNotFoundException("No active playlist selected");
        }
        return getPlaylist(playlistId);
    }

    public Optional<String> activeId() {
        return Optional.ofNullable(activePlaylistId.get());
    }

    @Transactional
    public Playlist create(String name, List<String> trackIds, PlaybackMode mode) {
        validateTracks(trackIds);
        return playlistRepository.save(new Playlist(UUID.randomUUID().toString(), name, trackIds, mode));
    }

    @Transactional
    public Playlist materializeCatalog(String name) {
        List<String> trackIds = trackRepository.findAll().stream().map(track -> track.id()).toList();
        return playlistRepository.save(new Playlist(UUID.randomUUID().toString(), name, trackIds,
                PlaybackMode.SEQUENTIAL));
    }

    @Transactional
    public Playlist rename(String playlistId, String name) {
        Playlist playlist = getPlaylist(playlistId);
        return playlistRepository.save(new Playlist(playlist.id(), name, playlist.trackIds(), playlist.playbackMode()));
    }

    @Transactional
    public Playlist setMode(String playlistId, PlaybackMode mode) {
        Playlist playlist = getPlaylist(playlistId);
        return playlistRepository.save(new Playlist(playlist.id(), playlist.name(), playlist.trackIds(), mode));
    }

    @Transactional
    public Playlist reorder(String playlistId, List<String> trackIds) {
        Playlist playlist = getPlaylist(playlistId);
        validateTracks(trackIds);
        return playlistRepository.save(new Playlist(playlist.id(), playlist.name(), trackIds, playlist.playbackMode()));
    }

    @Transactional
    public Playlist addEntries(String playlistId, List<String> trackIds) {
        Playlist playlist = getPlaylist(playlistId);
        validateTracks(trackIds);
        return reorder(playlistId, concat(playlist.trackIds(), trackIds));
    }

    @Transactional
    public Playlist removeEntries(String playlistId, List<String> trackIds) {
        Playlist playlist = getPlaylist(playlistId);
        var remaining = new ArrayList<>(playlist.trackIds());
        for (String trackId : trackIds) {
            remaining.remove(trackId);
        }
        return reorder(playlistId, remaining);
    }

    @Transactional
    public void delete(String playlistId) {
        getPlaylist(playlistId);
        playlistRepository.deleteById(playlistId);
    }

    private void validateTracks(List<String> trackIds) {
        var existingIds = trackRepository.findAll().stream().map(track -> track.id()).collect(Collectors.toSet());
        if (trackIds.stream().anyMatch(id -> !existingIds.contains(id))) {
            throw new IllegalArgumentException("Playlist contains an unknown track");
        }
    }

    private List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}