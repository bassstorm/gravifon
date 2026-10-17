package com.gravifon.player.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SessionAcquireRequest(@NotBlank @Size(max = 256) String username) {}
