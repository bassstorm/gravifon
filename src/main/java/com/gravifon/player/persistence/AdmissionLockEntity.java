package com.gravifon.player.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admission_lock")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AdmissionLockEntity {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AdmissionScope scope;
}
