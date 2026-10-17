package com.gravifon.player.streaming;

import com.gravifon.player.registry.model.StreamTrack;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface StreamProxy {
    void proxy(StreamTrack track, HttpServletRequest request, HttpServletResponse response) throws IOException;
}
