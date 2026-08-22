package com.gravifon.player.registry.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoOpTagWriteBackHandlerTest {
    @Test
    void acceptsFileMetadataWithoutChangingTheSource() {
        Track track = new Track("file", TrackKind.FILE, Map.of(), 1L, TrackState.healthy(),
                "song.mp3", "mp3", null, null, null);
        NoOpTagWriteBackHandler handler = new NoOpTagWriteBackHandler();

        assertThat(handler.supports(track)).isTrue();
        assertThat(handler.writeBack(track, Map.of()).accepted()).isTrue();
    }
}