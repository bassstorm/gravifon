package com.gravifon.player.registry.metadata;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@InfrastructureRing
@Slf4j
@Component
public class JaudiotaggerMetadataExtractor implements MetadataExtractor {

    private static final Map<FieldKey, String> STANDARD_KEYS = Map.ofEntries(
            Map.entry(FieldKey.ARTIST, "ARTIST"),
            Map.entry(FieldKey.ALBUM, "ALBUM"),
            Map.entry(FieldKey.TITLE, "TITLE"),
            Map.entry(FieldKey.ALBUM_ARTIST, "ALBUM_ARTIST"),
            Map.entry(FieldKey.YEAR, "DATE"),
            Map.entry(FieldKey.TRACK, "TRACK"),
            Map.entry(FieldKey.TRACK_TOTAL, "TRACK_TOTAL"),
            Map.entry(FieldKey.GENRE, "GENRE"));

    @Override
    public ExtractedMetadata extract(Path path) {
        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());
            Map<String, List<String>> values = new LinkedHashMap<>();
            Tag tag = audioFile.getTag();
            if (tag != null) {
                for (Map.Entry<FieldKey, String> entry : STANDARD_KEYS.entrySet()) {
                    addValues(values, entry.getValue(), tag.getFields(entry.getKey()));
                }
                var fields = tag.getFields();
                while (fields.hasNext()) {
                    TagField field = fields.next();
                    String key = field.getId().toUpperCase(Locale.ROOT);
                    if (!values.containsKey(key)) {
                        values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rawValue(field));
                    }
                }
            }
            int duration = audioFile.getAudioHeader().getTrackLength();
            return new ExtractedMetadata(values, duration > 0 ? (long) duration : null, true);
        } catch (Exception exception) {
            log.warn("Unable to extract metadata for {}. Keeping track with unknown metadata. Cause: {}",
                    path, exception.toString());
            return new ExtractedMetadata(Map.of(), null, false);
        }
    }

    private void addValues(Map<String, List<String>> values, String key, List<TagField> fields) {
        for (TagField field : fields) {
            String value = field.toString();
            if (!value.isBlank()) {
                values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            }
        }
    }

    private String rawValue(TagField field) {
        try {
            return new String(field.getRawContent(), StandardCharsets.UTF_8).trim();
        } catch (Exception exception) {
            return field.toString();
        }
    }
}