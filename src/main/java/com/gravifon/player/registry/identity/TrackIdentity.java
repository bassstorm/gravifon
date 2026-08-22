package com.gravifon.player.registry.identity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TrackIdentity {

    private TrackIdentity() {
    }

    public static String forFile(Path relativePath) {
        if (relativePath == null) {
            throw new IllegalArgumentException("Relative path is required");
        }
        return sha1("file:" + relativePath.toString().replace('\\', '/'));
    }

    public static String forStream(String sourceUrl, SourceUrlNormalizer normalizer) {
        if (normalizer == null) {
            throw new IllegalArgumentException("Source URL normalizer is required");
        }
        return sha1("stream:" + normalizer.normalize(sourceUrl));
    }

    private static String sha1(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 algorithm unavailable", exception);
        }
    }
}