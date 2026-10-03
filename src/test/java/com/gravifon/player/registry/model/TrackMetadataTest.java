package com.gravifon.player.registry.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrackMetadataTest {
    @Test
    void copiesMetadataAndNestedValues() {
        List<String> titles = new ArrayList<>(List.of("Song"));
        Map<String, List<String>> values = new HashMap<>();
        values.put("TITLE", titles);

        TrackMetadata metadata = new TrackMetadata(values);
        titles.add("Changed");
        values.put("ARTIST", List.of("Artist"));

        assertThat(metadata.values()).containsOnlyKeys("TITLE").containsEntry("TITLE", List.of("Song"));
        assertThatThrownBy(() -> metadata.values().put("ARTIST", List.of("Artist")))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> metadata.values().get("TITLE").add("Changed"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMalformedMetadata() {
        Map<String, List<String>> nullValues = new HashMap<>();
        nullValues.put("TITLE", null);
        Map<String, List<String>> blankKey = new HashMap<>();
        blankKey.put(" ", List.of("Song"));

        assertThatThrownBy(() -> new TrackMetadata(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Track metadata is required");
        assertThatThrownBy(() -> new TrackMetadata(nullValues))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be empty");
        assertThatThrownBy(() -> new TrackMetadata(blankKey))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("keys must not be blank");
        assertThatThrownBy(() -> new TrackMetadata(Map.of("TITLE", List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be empty");
        assertThatThrownBy(() -> new TrackMetadata(Map.of("TITLE", List.of(" "))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");
    }
}
