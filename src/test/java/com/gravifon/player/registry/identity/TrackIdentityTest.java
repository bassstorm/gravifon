package com.gravifon.player.registry.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TrackIdentityTest {
    @Test
    void fileIdentityUsesRelativePathAndStablePrefix() {
        assertThat(TrackIdentity.forFile(Path.of("albums/classic/track.mp3")))
            .isEqualTo("56efce21b99be1648bfce49a6d9cfae05f9049e4");
        assertThat(TrackIdentity.forFile(Path.of("albums/classic/./track.mp3")))
            .isEqualTo(TrackIdentity.forFile(Path.of("albums/classic/track.mp3")));
    }

    @Test
    void fileIdentityRejectsPathsOutsideLibraryRoot() {
        assertThatIllegalArgumentException().isThrownBy(() -> TrackIdentity.forFile(Path.of("../track.mp3")));
    }

    @Test
    void streamIdentityIgnoresVolatileQueryParameters() {
        assertThat(TrackIdentity.forStream(URI.create("HTTPS://Example.COM/song?id=42&utm_source=mail")))
            .isEqualTo(TrackIdentity.forStream(URI.create("https://example.com/song?id=42&fbclid=ignored")));
    }

    @Test
    void normalizerPreservesNonVolatileQueryParameters() {
        assertThat(SourceNormalizer.normalize(URI.create("https://Example.COM/song?token=abc&utm_medium=mail")))
            .isEqualTo(URI.create("https://example.com/song?token=abc"));
    }

    @Test
    void normalizerCanonicalizesQueryParameterOrder() {
        assertThat(SourceNormalizer.normalize(URI.create("https://example.com/song?b=2&a=1")))
            .isEqualTo(URI.create("https://example.com/song?a=1&b=2"));
    }
}
