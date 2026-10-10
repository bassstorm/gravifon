package com.gravifon.player.config;

import java.nio.file.Path;

@FunctionalInterface
public interface MusicLibraryProbe {
    boolean isReadable(Path path);
}
