package com.gravifon.player.registry.stream;

public class StreamResolutionException extends RuntimeException {
    public StreamResolutionException() {
        super("Stream resolver failed to return valid tracks");
    }

    public StreamResolutionException(Throwable cause) {
        super("Stream resolver failed to return valid tracks", cause);
    }
}
