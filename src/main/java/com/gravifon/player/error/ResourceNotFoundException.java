package com.gravifon.player.error;

import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
