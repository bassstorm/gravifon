package com.gravifon.player.playback.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class PlaybackStateRestoreRunner implements ApplicationRunner {
    private final PlaybackService playbackService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Restoring playback state on startup");
        playbackService.restoreState();
    }
}
