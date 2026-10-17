package com.gravifon.player.session.repository;

import com.gravifon.player.session.model.User;
import com.gravifon.player.session.model.UserId;
import java.util.Optional;
import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public interface UserRepository {
    Optional<User> findByNormalizedName(String normalizedName);

    Optional<User> findById(UserId id);

    User save(User user);

    /**
     * Serializes creation of users. Must be called before resolving a user by name when the user may not exist yet.
     */
    void lockCreation();

    /**
     * Acquires the serialization lock for this user before changes to sessions or user-owned state. Lock in order:
     * admission, user, then session/context/playlist state, then shared tracks in sorted ID order.
     */
    Optional<User> lockById(UserId id);
}
