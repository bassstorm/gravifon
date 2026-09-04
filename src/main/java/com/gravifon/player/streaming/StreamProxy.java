package com.gravifon.player.streaming;

import com.gravifon.player.registry.model.Track;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface StreamProxy {
    void proxy(Track track, HttpServletRequest request, HttpServletResponse response) throws IOException;
}
