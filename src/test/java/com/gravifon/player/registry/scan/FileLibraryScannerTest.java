package com.gravifon.player.registry.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gravifon.player.registry.metadata.MetadataExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLibraryScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void retainsUnreadableFilesWithEmptyMetadata() throws Exception {
        MetadataExtractor extractor = mock(MetadataExtractor.class);
        Path file = Files.createFile(tempDir.resolve("broken.mp3"));
        when(extractor.extract(file)).thenThrow(new IllegalStateException("malformed tags"));
        FileLibraryScanner scanner = new FileLibraryScanner(extractor);

        assertThat(scanner.scan(tempDir)).singleElement().satisfies(scanFile -> {
            assertThat(scanFile.readable()).isFalse();
            assertThat(scanFile.metadata()).isEmpty();
        });
    }
}
