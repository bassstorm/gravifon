package com.gravifon.player.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPlaylistStore extends JpaRepository<PlaylistEntity, String> {}
