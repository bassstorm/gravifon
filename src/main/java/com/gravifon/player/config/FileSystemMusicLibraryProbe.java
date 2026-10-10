package com.gravifon.player.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class FileSystemMusicLibraryProbe implements MusicLibraryProbe {
    @Override
    public boolean isReadable(Path path) {
        return Files.exists(path) && Files.isReadable(path);
    }
}
