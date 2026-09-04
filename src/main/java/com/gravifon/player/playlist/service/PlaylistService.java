package com.gravifon.player.playlist.service;

import com.gravifon.player.error.ResourceNotFoundException;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.repository.PlaylistRepository;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.repository.TrackRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;
    private final AtomicReference<String> activePlaylistId = new AtomicReference<>();

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
        return playlistRepository.save(playlist.rename(name));
    }

    @Transactional
    public Playlist setMode(String playlistId, PlaybackMode mode) {
        Playlist playlist = getPlaylist(playlistId);
        return playlistRepository.save(playlist.setMode(mode));
    }

    @Transactional
    public Playlist reorder(String playlistId, List<String> trackIds) {
        Playlist playlist = getPlaylist(playlistId);
        validateTracks(trackIds);
        return playlistRepository.save(playlist.reorder(trackIds));
    }

    @Transactional
    public Playlist addEntries(String playlistId, List<String> trackIds) {
        Playlist playlist = getPlaylist(playlistId);
        validateTracks(trackIds);
        return playlistRepository.save(playlist.addEntries(trackIds));
    }

    @Transactional
    public Playlist removeEntries(String playlistId, List<String> trackIds) {
        Playlist playlist = getPlaylist(playlistId);
        return playlistRepository.save(playlist.removeEntries(trackIds));
    }

    @Transactional
    public void delete(String playlistId) {
        getPlaylist(playlistId);
        playlistRepository.deleteById(playlistId);
    }

    private void validateTracks(List<String> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return;
        }
        var foundTracks = trackRepository.findAllById(trackIds);
        var existingIds = foundTracks.stream().map(Track::id).collect(Collectors.toSet());
        if (trackIds.stream().anyMatch(id -> !existingIds.contains(id))) {
            throw new IllegalArgumentException("Playlist contains an unknown track");
        }
    }
}