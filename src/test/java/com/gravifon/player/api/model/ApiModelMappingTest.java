package com.gravifon.player.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiModelMappingTest {
    @Test
    void requestModelsExposeDeclarativeValidationConstraints() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            TrackMetadataUpdateRequest missingMetadata = new TrackMetadataUpdateRequest(null);
            TrackMetadataUpdateRequest invalidMetadata = new TrackMetadataUpdateRequest(Map.of("TITLE", List.of("")));

            assertThat(validator.validate(missingMetadata))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("metadata");
            assertThat(validator.validate(invalidMetadata))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("metadata[TITLE].<map value>[0].<list element>");
        }
    }

    @Test
    void trackResponse_fromMapsFields() {
        Track track =
                new FileTrack(
                        "track-1",
                        new TrackMetadata(Map.of()),
                        42L,
                        TrackState.healthy(),
                        Path.of("a.mp3"),
                        "mp3"
        );

        TrackResponse response = TrackResponse.from(track);

        assertThat(response.id()).isEqualTo("track-1");
        assertThat(response.filename()).isEqualTo("a.mp3");
        assertThat(response.format()).isEqualTo("mp3");
        assertThat(response.durationSeconds()).isEqualTo(42L);
    }
}
