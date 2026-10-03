package com.gravifon.player.error;

import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
