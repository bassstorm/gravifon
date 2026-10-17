package com.gravifon.player.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "app_user")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class UserEntity {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "normalized_name", nullable = false, length = 64)
    private String normalizedName;
    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserEntity(String id, String normalizedName, String displayName, Instant createdAt) {
        this.id = id;
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }
}
