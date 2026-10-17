package com.gravifon.player.registry.scan;

import com.gravifon.player.registry.identity.TrackIdentity;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ScanReconciler {
    public List<Decision> reconcile(
            List<Track> existing,
            List<LibraryScanner.ScanFile> scanned,
            Set<String> referencedTrackIds
    ) {
        List<ScannedFile> indexedFiles =
                scanned
            .stream()
            .map(file -> new ScannedFile(TrackIdentity.forFile(file.relativePath()), file))
            .toList();
        Map<String, LibraryScanner.ScanFile> filesById =
                indexedFiles.stream().collect(Collectors.toMap(ScannedFile::trackId, ScannedFile::file));
        Map<String, Track> existingFilesById = existing
            .stream()
            .filter(track -> track.kind() == TrackKind.FILE)
            .collect(Collectors.toMap(Track::id, Function.identity(), (first, second) -> first));

        List<Decision> decisions = existingFilesById
            .values()
            .stream()
            .filter(track -> !filesById.containsKey(track.id()))
            .map(track -> referencedTrackIds.contains(track.id())
                    ? new Decision(DecisionType.TOMBSTONE, track.id(), null)
                    : new Decision(DecisionType.REMOVE, track.id(), null))
            .collect(Collectors.toList());

        decisions.addAll(indexedFiles.stream().map(indexed -> {
            String fileId = indexed.trackId();
            LibraryScanner.ScanFile file = indexed.file();
            Track existingTrack = existingFilesById.get(fileId);
            return new Decision(existingTrack == null ? DecisionType.CREATE : DecisionType.REFRESH, fileId, file);
        }).toList());

        return List.copyOf(decisions);
    }

    public enum DecisionType {
        CREATE,
        REFRESH,
        TOMBSTONE,
        REMOVE
    }

    public record Decision(DecisionType type, String trackId, LibraryScanner.ScanFile file) {}

    private record ScannedFile(String trackId, LibraryScanner.ScanFile file) {}
}
