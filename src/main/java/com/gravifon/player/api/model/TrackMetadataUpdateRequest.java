package com.gravifon.player.api.model;

import java.util.List;
import java.util.Map;

public record TrackMetadataUpdateRequest(Map<String, List<String>> metadata) {}
