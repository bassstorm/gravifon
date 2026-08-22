package com.gravifon.player.registry.scan;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.identity.TrackIdentity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScanReconciler {

    public List<Decision> reconcile(List<Track> existing, List<LibraryScanner.ScanFile> scanned,
                                    Set<String> referencedTrackIds) {
        Map<String, LibraryScanner.ScanFile> filesById = scanned.stream()
                .collect(Collectors.toMap(file -> TrackIdentity.forFile(Path.of(file.relativePath())), Function.identity()));
        List<Decision> decisions = existing.stream()
                .filter(track -> track.kind() == TrackKind.FILE && !filesById.containsKey(track.id()))
                .map(track -> referencedTrackIds.contains(track.id())
                        ? new Decision(DecisionType.TOMBSTONE, track.id(), null)
                        : new Decision(DecisionType.REMOVE, track.id(), null))
                .collect(Collectors.toList());
        decisions.addAll(scanned.stream().map(file -> {
            String fileId = TrackIdentity.forFile(Path.of(file.relativePath()));
            Track existingTrack = existing.stream().filter(track -> track.kind() == TrackKind.FILE
                    && fileId.equals(track.id()))
                    .findFirst().orElse(null);
            return new Decision(existingTrack == null ? DecisionType.CREATE : DecisionType.REFRESH,
                    existingTrack == null ? null : existingTrack.id(), file);
        }).toList());
        return List.copyOf(decisions);
    }

    public enum DecisionType { CREATE, REFRESH, TOMBSTONE, REMOVE }

    public record Decision(DecisionType type, String trackId, LibraryScanner.ScanFile file) {
    }
}