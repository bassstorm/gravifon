package com.gravifon.player.registry.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.metadata.MetadataExtractor;
import com.gravifon.player.registry.identity.TrackIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.common.jimfs.Jimfs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;

class ScanReconcilerTest {

        @Test
        void libraryScannerUsesFilesystemSeamAndIgnoresUnsupportedFiles() throws Exception {
                Path root = Jimfs.newFileSystem().getPath("/music");
                Files.createDirectories(root.resolve("nested"));
                Files.createFile(root.resolve("nested/song.mp3"));
                Files.createFile(root.resolve("cover.jpg"));
                MetadataExtractor extractor = mock(MetadataExtractor.class);
                when(extractor.extract(any())).thenReturn(new MetadataExtractor.ExtractedMetadata(
                                Map.of("TITLE", List.of("Song")), 90L));

                List<LibraryScanner.ScanFile> files = new FileLibraryScanner(extractor).scan(root);

                assertThat(files).extracting(LibraryScanner.ScanFile::relativePath)
                                .containsExactly("nested/song.mp3");
                assertThat(files.getFirst().metadata()).containsEntry("TITLE", List.of("Song"));
        }

    @Test
    void tombstonesReferencedMissingFilesAndRemovesUnreferencedFiles() {
        Track referenced = fileTrack("referenced", "kept.mp3");
        Track unreferenced = fileTrack("unreferenced", "removed.mp3");

        List<ScanReconciler.Decision> decisions = new ScanReconciler().reconcile(
                List.of(referenced, unreferenced), List.of(), Set.of(referenced.id()));

        assertThat(decisions).extracting(ScanReconciler.Decision::type)
                .containsExactlyInAnyOrder(ScanReconciler.DecisionType.TOMBSTONE,
                        ScanReconciler.DecisionType.REMOVE);
    }

    @Test
    void createsNewFilesAndRefreshesKnownFiles() {
        Track existing = fileTrack(TrackIdentity.forFile(java.nio.file.Path.of("known.mp3")), "known.mp3");
        List<LibraryScanner.ScanFile> files = List.of(
                new LibraryScanner.ScanFile("known.mp3", "mp3", true, 120L),
                new LibraryScanner.ScanFile("new.ogg", "ogg", true, 90L));

        List<ScanReconciler.Decision> decisions = new ScanReconciler().reconcile(
                List.of(existing), files, Set.of());

        assertThat(decisions).extracting(ScanReconciler.Decision::type)
                .containsExactly(ScanReconciler.DecisionType.REFRESH, ScanReconciler.DecisionType.CREATE);
    }

    @Test
    void emitsRefreshForUnreadableAndReturningFiles() {
        Track existing = fileTrack(TrackIdentity.forFile(java.nio.file.Path.of("known.mp3")), "known.mp3");

        assertThat(new ScanReconciler().reconcile(List.of(existing), List.of(
                new LibraryScanner.ScanFile("known.mp3", "mp3", false, Map.of(), null)), Set.of()))
                .extracting(ScanReconciler.Decision::type)
                .containsExactly(ScanReconciler.DecisionType.REFRESH);
        assertThat(new ScanReconciler().reconcile(List.of(existing), List.of(
                new LibraryScanner.ScanFile("known.mp3", "mp3", true, Map.of(), 100L)), Set.of()))
                .extracting(ScanReconciler.Decision::type)
                .containsExactly(ScanReconciler.DecisionType.REFRESH);
    }

        @Test
        void refreshesReturningTrackUsingItsCanonicalIdentity() {
                String id = TrackIdentity.forFile(java.nio.file.Path.of("missing.mp3"));
                Track existing = fileTrack(id, "missing.mp3");

                List<ScanReconciler.Decision> decisions = new ScanReconciler().reconcile(
                                List.of(existing),
                                List.of(new LibraryScanner.ScanFile("missing.mp3", "mp3", true, Map.of("TITLE", List.of("Back")), 100L)),
                                Set.of());

                assertThat(decisions).singleElement().satisfies(decision -> {
                        assertThat(decision.type()).isEqualTo(ScanReconciler.DecisionType.REFRESH);
                        assertThat(decision.trackId()).isEqualTo(id);
                        assertThat(decision.file().metadata()).containsEntry("TITLE", List.of("Back"));
                });
        }

    private Track fileTrack(String id, String path) {
        return new Track(id, TrackKind.FILE, Map.of(), 60L, TrackState.healthy(), path, "mp3", null, null, null);
    }
}