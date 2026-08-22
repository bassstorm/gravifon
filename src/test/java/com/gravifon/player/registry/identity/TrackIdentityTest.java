package com.gravifon.player.registry.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TrackIdentityTest {

    private final SourceUrlNormalizer normalizer = new DefaultSourceUrlNormalizer();

    @Test
    void fileIdentityUsesRelativePathAndStablePrefix() {
        assertThat(TrackIdentity.forFile(Path.of("albums/classic/track.mp3")))
            .isEqualTo("56efce21b99be1648bfce49a6d9cfae05f9049e4");
    }

    @Test
    void streamIdentityIgnoresVolatileQueryParameters() {
        assertThat(TrackIdentity.forStream("HTTPS://Example.COM/song?id=42&utm_source=mail", normalizer))
                .isEqualTo(TrackIdentity.forStream("https://example.com/song?id=42&fbclid=ignored", normalizer));
    }

    @Test
    void normalizerPreservesNonVolatileQueryParameters() {
        assertThat(normalizer.normalize("https://Example.COM/song?token=abc&utm_medium=mail"))
                .isEqualTo("https://example.com/song?token=abc");
    }

    @Test
    void normalizerCanonicalizesQueryParameterOrder() {
        assertThat(normalizer.normalize("https://example.com/song?b=2&a=1"))
                .isEqualTo("https://example.com/song?a=1&b=2");
    }
}