package com.gravifon.player.playlist.service;

import com.gravifon.player.playlist.repository.PlaylistRepository;
import com.gravifon.player.registry.scan.ReferencedTrackIdsPort;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaylistReferencedTrackIdsAdapter implements ReferencedTrackIdsPort {

    private final PlaylistRepository playlistRepository;

    @Override
    public Set<String> getReferencedTrackIds() {
        return playlistRepository.findAll().stream()
                .flatMap(playlist -> playlist.trackIds().stream())
                .collect(Collectors.toSet());
    }
}
