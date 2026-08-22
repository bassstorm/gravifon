package com.gravifon.player.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTrackStore extends JpaRepository<TrackEntity, String> {
}
