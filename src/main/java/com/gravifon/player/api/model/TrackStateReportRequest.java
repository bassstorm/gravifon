package com.gravifon.player.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

public record TrackStateReportRequest(String kind, String message, boolean clear) {
    @AssertTrue(message = "failure reports require a non-blank message and kind must not be blank")
    @JsonIgnore
    public boolean isValidReport() {
        return clear || (message != null && !message.isBlank() && (kind == null || !kind.isBlank()));
    }
}
