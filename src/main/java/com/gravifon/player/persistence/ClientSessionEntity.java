package com.gravifon.player.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "client_session")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ClientSessionEntity {
    @Id
    @Column(length = 36)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    public ClientSessionEntity(
            String id,
            UserEntity user,
            Instant createdAt,
            Instant lastActivityAt,
            Instant invalidatedAt
    ) {
        this.id = id;
        this.user = user;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
        this.invalidatedAt = invalidatedAt;
    }
}
