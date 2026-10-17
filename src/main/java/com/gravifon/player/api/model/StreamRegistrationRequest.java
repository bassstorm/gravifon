package com.gravifon.player.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record StreamRegistrationRequest(@NotEmpty List<@NotBlank String> sourceUrls) {}
