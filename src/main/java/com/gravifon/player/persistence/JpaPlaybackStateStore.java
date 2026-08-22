package com.gravifon.player.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPlaybackStateStore extends JpaRepository<PlaybackStateEntity, String> {
}
