package com.gravifon.player.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record PositionReportRequest(@NotBlank String trackId, @PositiveOrZero long positionSeconds) {}
