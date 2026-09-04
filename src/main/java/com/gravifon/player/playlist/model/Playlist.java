package com.gravifon.player.playlist.model;

import com.gravifon.player.playback.model.PlaybackMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jmolecules.ddd.types.AggregateRoot;

public record Playlist(
        String id,
        String name,
        List<String> trackIds,
        PlaybackMode playbackMode
) implements AggregateRoot<Playlist, PlaylistId> {

    public Playlist(String id, String name, List<String> trackIds) {
        this(id, name, trackIds, PlaybackMode.SEQUENTIAL);
    }

    public Playlist {
        trackIds = List.copyOf(trackIds);
        playbackMode = playbackMode == null ? PlaybackMode.SEQUENTIAL : playbackMode;
    }

    public PlaylistId playlistId() {
        return PlaylistId.of(id);
    }

    @Override
    public PlaylistId getId() {
        return playlistId();
    }

    public Playlist rename(String newName) {
        return new Playlist(id, newName, trackIds, playbackMode);
    }

    public Playlist setMode(PlaybackMode newMode) {
        return new Playlist(id, name, trackIds, newMode);
    }

    public Playlist reorder(List<String> newTrackIds) {
        return new Playlist(id, name, newTrackIds, playbackMode);
    }

    public Playlist addEntries(List<String> additions) {
        List<String> updated = Stream.concat(trackIds.stream(), additions.stream()).toList();
        return new Playlist(id, name, updated, playbackMode);
    }

    public Playlist removeEntries(List<String> removals) {
        List<String> updated = new ArrayList<>(trackIds);
        for (String removal : removals) {
            updated.remove(removal);
        }
        return new Playlist(id, name, updated, playbackMode);
    }
}

