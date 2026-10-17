package com.gravifon.player.registry.identity;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

public final class SourceNormalizer {
    private SourceNormalizer() {
    }

    public static Path normalize(Path relativePath) {
        if (relativePath == null || relativePath.toString().isBlank() || relativePath.isAbsolute()) {
            throw new IllegalArgumentException("Relative path is required");
        }

        Path normalized = relativePath.normalize();
        if (normalized.toString().isBlank() || normalized.startsWith("..")) {
            throw new IllegalArgumentException("Relative path must not escape its root");
        }
        return normalized;
    }

    public static String toCanonicalString(Path relativePath) {
        Path normalized = normalize(relativePath);
        StringBuilder canonical = new StringBuilder();
        for (Path segment : normalized) {
            if (!canonical.isEmpty()) {
                canonical.append('/');
            }
            canonical.append(segment);
        }
        return canonical.toString();
    }

    public static URI normalize(URI sourceUrl) {
        if (sourceUrl == null) {
            throw new IllegalArgumentException("Source URL is required");
        }
        URI normalizedPath = sourceUrl.normalize();
        if (normalizedPath.getScheme() == null || normalizedPath.getHost() == null) {
            throw new IllegalArgumentException("Source URL must include a scheme and host");
        }

        String query = normalizedPath.getRawQuery();
        String normalizedQuery = query == null
                ? null
                : Arrays
            .stream(query.split("&"))
            .filter(parameter -> !parameter.isBlank())
            .filter(parameter -> !isVolatile(parameter))
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.joining("&"));

        StringBuilder normalized =
                new StringBuilder().append(normalizedPath.getScheme().toLowerCase(Locale.ROOT)).append("://");
        if (normalizedPath.getRawUserInfo() != null) {
            normalized.append(normalizedPath.getRawUserInfo()).append('@');
        }
        normalized.append(normalizedPath.getHost().toLowerCase(Locale.ROOT));
        if (normalizedPath.getPort() >= 0) {
            normalized.append(':').append(normalizedPath.getPort());
        }
        if (normalizedPath.getRawPath() != null) {
            normalized.append(normalizedPath.getRawPath());
        }
        if (normalizedQuery != null && !normalizedQuery.isBlank()) {
            normalized.append('?').append(normalizedQuery);
        }
        if (normalizedPath.getRawFragment() != null) {
            normalized.append('#').append(normalizedPath.getRawFragment());
        }

        try {
            return new URI(normalized.toString());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid source URL", exception);
        }
    }

    private static boolean isVolatile(String parameter) {
        int separator = parameter.indexOf('=');
        String key = (separator >= 0 ? parameter.substring(0, separator) : parameter).toLowerCase(Locale.ROOT);
        return key.startsWith("utm_") || key.equals("fbclid") || key.equals("gclid");
    }
}
