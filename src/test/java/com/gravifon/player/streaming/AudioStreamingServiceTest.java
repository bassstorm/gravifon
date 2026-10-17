package com.gravifon.player.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

class AudioStreamingServiceTest {
    private final AudioStreamingService service = new AudioStreamingService();

    private Resource resourceOfLength(long length) {
        return new AbstractResource() {
            @Override
            public String getDescription() {
                return "test-resource";
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[(int) length]);
            }

            @Override
            public long contentLength() {
                return length;
            }
        };
    }

    @Test
    void parseSingleRegion_singleRange() {
        Resource resource = resourceOfLength(100);
        ResourceRegion region = service.parseSingleRegion("bytes=10-20", resource, 100);
        assertThat(region.getPosition()).isEqualTo(10L);
        assertThat(region.getCount()).isEqualTo(11L);
    }

    @Test
    void parseSingleRegion_openEndedRange() {
        Resource resource = resourceOfLength(100);
        ResourceRegion region = service.parseSingleRegion("bytes=50-", resource, 100);
        assertThat(region.getPosition()).isEqualTo(50L);
        assertThat(region.getCount()).isEqualTo(50L);
    }

    @Test
    void parseSingleRegion_suffixRange() {
        Resource resource = resourceOfLength(100);
        ResourceRegion region = service.parseSingleRegion("bytes=-20", resource, 100);
        assertThat(region.getPosition()).isEqualTo(80L);
        assertThat(region.getCount()).isEqualTo(20L);
    }

    @Test
    void parseSingleRegion_unsatisfiableRangeEmitsContentRangeHeader() {
        Resource resource = resourceOfLength(100);
        assertThatThrownBy(() -> service.parseSingleRegion("bytes=100-200", resource, 100))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode().value()).isEqualTo(416);
                assertThat(ex.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */100");
            });
    }

    @Test
    void parseSingleRegion_malformedRangeEmitsContentRangeHeader() {
        Resource resource = resourceOfLength(100);
        assertThatThrownBy(() -> service.parseSingleRegion("invalid", resource, 100))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode().value()).isEqualTo(416);
                assertThat(ex.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */100");
            });

        assertThatThrownBy(() -> service.parseSingleRegion("bytes=40-20", resource, 100))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode().value()).isEqualTo(416);
                assertThat(ex.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */100");
            });
    }

    @Test
    void parseSingleRegion_multipartRangeRejectedWith416() {
        Resource resource = resourceOfLength(100);
        assertThatThrownBy(() -> service.parseSingleRegion("bytes=0-10,20-30", resource, 100))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode().value()).isEqualTo(416);
                assertThat(ex.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */100");
            });
    }
}
