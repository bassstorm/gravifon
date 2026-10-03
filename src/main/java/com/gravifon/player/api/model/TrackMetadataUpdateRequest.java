package com.gravifon.player.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record TrackMetadataUpdateRequest(@NotNull Map<@NotBlank String, @NotEmpty List<@NotBlank String>> metadata) {}
