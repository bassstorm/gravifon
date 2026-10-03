package com.gravifon.player.registry.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TrackMetadata(Map<String, List<String>> values) {
    public TrackMetadata {
        if (values == null) {
            throw new IllegalArgumentException("Track metadata is required");
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        values.forEach((key, entries) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Metadata keys must not be blank");
            }
            if (entries == null || entries.isEmpty()) {
                throw new IllegalArgumentException("Metadata values must not be empty for key: " + key);
            }
            if (entries
                .stream()
                .anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("Metadata values must not be blank for key: " + key);
            }
            normalized.put(key, List.copyOf(entries));
        });
        values = Collections.unmodifiableMap(normalized);
    }
}
