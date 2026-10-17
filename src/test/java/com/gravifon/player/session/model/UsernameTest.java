package com.gravifon.player.session.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernameTest {
    @Test
    void trimsAndNormalizesWithoutChangingDisplaySpelling() {
        Username first = Username.from("  Alice_9  ");
        Username equivalent = Username.from("alice_9");

        assertThat(first.normalizedName()).isEqualTo("alice_9");
        assertThat(first.displayName()).isEqualTo("Alice_9");
        assertThat(equivalent.normalizedName()).isEqualTo(first.normalizedName());
        assertThat(equivalent.displayName()).isEqualTo("alice_9");
    }

    @Test
    void acceptsAllAllowedAsciiCharactersAndMaximumLength() {
        Username username = Username.from("A-z_09.");
        Username maximum = Username.from("a".repeat(64));

        assertThat(username.normalizedName()).isEqualTo("a-z_09.");
        assertThat(maximum.displayName()).hasSize(64);
    }

    @Test
    void rejectsMissingBlankOversizedOrUnsupportedNames() {
        assertThatThrownBy(() -> Username.from(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Username.from(" \t ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Username.from("a".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Username.from("name with space")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Username.from("álîce")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInconsistentDirectConstruction() {
        assertThatThrownBy(() -> new Username("alice", "Alice ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Username("ALICE", "Alice")).isInstanceOf(IllegalArgumentException.class);
    }
}
