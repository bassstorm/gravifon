package com.gravifon.player.registry.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class JaudiotaggerMetadataExtractorTest {

    @Test
    void malformedAudioRemainsAvailableWithUnknownDuration() {
        MetadataExtractor.ExtractedMetadata metadata =
                new JaudiotaggerMetadataExtractor().extract(Path.of("does-not-exist.mp3"));

        assertThat(metadata.values()).isEmpty();
        assertThat(metadata.durationSeconds()).isNull();
        assertThat(metadata.readable()).isFalse();
    }

    @Test
    void missingTagsStillProduceReadableMetadata() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);
        when(audioFile.getTag()).thenReturn(null);
        when(audioFile.getAudioHeader()).thenReturn(header);
        when(header.getTrackLength()).thenReturn(45);

        try (MockedStatic<AudioFileIO> audioFiles = org.mockito.Mockito.mockStatic(AudioFileIO.class)) {
            audioFiles.when(() -> AudioFileIO.read(any())).thenReturn(audioFile);

            MetadataExtractor.ExtractedMetadata metadata =
                    new JaudiotaggerMetadataExtractor().extract(Path.of("untagged.mp3"));

            assertThat(metadata.readable()).isTrue();
            assertThat(metadata.values()).isEmpty();
            assertThat(metadata.durationSeconds()).isEqualTo(45L);
        }
    }

    @Test
    void preservesMultipleValuesForGenre() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);
        Tag tag = mock(Tag.class);
        TagField first = mock(TagField.class);
        TagField second = mock(TagField.class);
        when(audioFile.getTag()).thenReturn(tag);
        when(audioFile.getAudioHeader()).thenReturn(header);
        when(header.getTrackLength()).thenReturn(90);
        when(tag.getFields(FieldKey.GENRE)).thenReturn(List.of(first, second));
        when(tag.getFields(any(FieldKey.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0) == FieldKey.GENRE ? List.of(first, second) : Collections.emptyList());
        when(tag.getFields()).thenReturn(Collections.emptyIterator());
        when(first.toString()).thenReturn("ambient");
        when(second.toString()).thenReturn("downtempo");

        try (MockedStatic<AudioFileIO> audioFiles = org.mockito.Mockito.mockStatic(AudioFileIO.class)) {
            audioFiles.when(() -> AudioFileIO.read(any())).thenReturn(audioFile);

            MetadataExtractor.ExtractedMetadata metadata =
                    new JaudiotaggerMetadataExtractor().extract(Path.of("tagged.mp3"));

            assertThat(metadata.readable()).isTrue();
            assertThat(metadata.values()).containsEntry("GENRE", List.of("ambient", "downtempo"));
        }
    }
}
