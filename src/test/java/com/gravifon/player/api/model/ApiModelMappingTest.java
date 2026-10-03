package com.gravifon.player.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiModelMappingTest {
    @Test
    void requestModelsSerializeWithoutValidationHelperProperties() throws Exception {
        String json =
                new ObjectMapper()
            .writeValueAsString(new PlaylistCreateRequest("Mix", List.of(), List.of(), false, null));

        assertThat(json).contains("\"name\":\"Mix\"");
        assertThat(json).doesNotContain("isValid");
    }

    @Test
    void requestModelsExposeDeclarativeValidationConstraints() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            PlaylistCreateRequest blankName = new PlaylistCreateRequest(" ", List.of(), List.of(), false, null);
            PlaylistUpdateRequest omittedName = new PlaylistUpdateRequest(null, List.of(), List.of(), List.of(), null);
            PlaylistUpdateRequest blankUpdateName =
                    new PlaylistUpdateRequest(" ", List.of(), List.of(), List.of(), null);
            PositionReportRequest negativePosition = new PositionReportRequest("", -1);
            TrackMetadataUpdateRequest missingMetadata = new TrackMetadataUpdateRequest(null);
            TrackMetadataUpdateRequest invalidMetadata = new TrackMetadataUpdateRequest(Map.of("TITLE", List.of("")));

            assertThat(validator.validate(blankName))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
            assertThat(validator.validate(omittedName)).isEmpty();
            assertThat(validator.validate(blankUpdateName))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("nameValid");
            assertThat(validator.validate(negativePosition))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("trackId", "positionSeconds");
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
        Track track = new FileTrack("track-1", Map.of(), 42L, TrackState.healthy(), "a.mp3", "mp3");

        TrackResponse response = TrackResponse.from(track);

        assertThat(response.id()).isEqualTo("track-1");
        assertThat(response.filename()).isEqualTo("a.mp3");
        assertThat(response.format()).isEqualTo("mp3");
        assertThat(response.durationSeconds()).isEqualTo(42L);
    }

    @Test
    void playlistResponse_fromSetsActiveFlagByIdMatch() {
        Playlist playlist = new Playlist("p1", "Playlist", List.of("t1"));

        PlaylistResponse active = PlaylistResponse.from(playlist, "p1");
        PlaylistResponse inactive = PlaylistResponse.from(playlist, "other");

        assertThat(active.active()).isTrue();
        assertThat(inactive.active()).isFalse();
    }

    @Test
    void playbackStateResponse_fromMapsAndNormalizesEnumValues() {
        PlaybackState state = new PlaybackState("all-tracks", "t1", PlaybackMode.RANDOM, TransportState.PAUSED, 8);

        PlaybackStateResponse response = PlaybackStateResponse.from(state);

        assertThat(response.activePlaylistId()).isEqualTo("all-tracks");
        assertThat(response.currentTrackId()).isEqualTo("t1");
        assertThat(response.playbackMode()).isEqualTo("random");
        assertThat(response.transportState()).isEqualTo("paused");
        assertThat(response.positionSeconds()).isEqualTo(8);
    }
}
