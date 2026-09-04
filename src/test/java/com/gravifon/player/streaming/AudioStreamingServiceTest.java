package com.gravifon.player.streaming;

import com.gravifon.player.playback.service.PlaybackService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AudioStreamingServiceTest {

    private final PlaybackService playbackService = mock(PlaybackService.class);
    private final AudioStreamingService service = new AudioStreamingService(playbackService);

    @Test
    void parseRange_parsesStartAndEnd() {
        AudioStreamingService.ByteRange range = service.parseRange("bytes=10-20", 100);
        assertThat(range.start()).isEqualTo(10L);
        assertThat(range.end()).isEqualTo(20L);
    }

    @Test
    void parseRange_parsesOpenEnded() {
        AudioStreamingService.ByteRange range = service.parseRange("bytes=50-", 100);
        assertThat(range.start()).isEqualTo(50L);
        assertThat(range.end()).isEqualTo(99L);
    }

    @Test
    void parseRange_parsesSuffix() {
        AudioStreamingService.ByteRange range = service.parseRange("bytes=-20", 100);
        assertThat(range.start()).isEqualTo(80L);
        assertThat(range.end()).isEqualTo(99L);
    }

    @Test
    void parseRange_rejectsInvalidRanges() {
        assertThatThrownBy(() -> service.parseRange("invalid", 100))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.parseRange("bytes=100-200", 50))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.parseRange("bytes=40-20", 100))
                .isInstanceOf(ResponseStatusException.class);
    }
}
