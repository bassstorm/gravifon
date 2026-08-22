package com.gravifon.player.registry.metadata;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NoOpTagWriteBackHandler implements TagWriteBackHandler {
    @Override
    public boolean supports(Track track) {
        return track.kind() == TrackKind.FILE;
    }

    @Override
    public WriteBackResult writeBack(Track track, Map<String, List<String>> metadata) {
        return new WriteBackResult(true, "File tag write-back is disabled");
    }
}