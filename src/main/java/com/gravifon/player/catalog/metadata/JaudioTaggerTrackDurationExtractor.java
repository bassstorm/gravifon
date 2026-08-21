package com.gravifon.player.catalog.metadata;

import java.nio.file.Path;
import java.util.OptionalLong;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.TagException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JaudioTaggerTrackDurationExtractor implements TrackDurationExtractor {


    @Override
    public OptionalLong extractDurationSeconds(Path path) {
        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());
            int length = audioFile.getAudioHeader().getTrackLength();
            return length > 0 ? OptionalLong.of(length) : OptionalLong.empty();
        } catch (CannotReadException | InvalidAudioFrameException | ReadOnlyFileException | TagException | RuntimeException e) {
            log.warn("Unable to extract track duration for {}. Falling back to unknown duration. Cause: {}", path, e.toString());
            return OptionalLong.empty();
        } catch (Exception e) {
            log.warn("Unable to extract track duration for {}. Falling back to unknown duration. Cause: {}", path, e.toString());
            return OptionalLong.empty();
        }
    }
}


