package com.gravifon.player.registry.identity;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TrackIdentity {
    private TrackIdentity() {
    }

    public static String forFile(Path relativePath) {
        return sha1("file:" + SourceNormalizer.toCanonicalString(relativePath));
    }

    public static String forStream(URI sourceUrl) {
        return sha1("stream:" + SourceNormalizer.normalize(sourceUrl));
    }

    private static String sha1(String value) {
        try {
            return HexFormat
                .of()
                .formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 algorithm unavailable", exception);
        }
    }
}
