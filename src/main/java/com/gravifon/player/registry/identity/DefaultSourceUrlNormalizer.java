package com.gravifon.player.registry.identity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

public class DefaultSourceUrlNormalizer implements SourceUrlNormalizer {
    @Override
    public String normalize(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Source URL is required");
        }
        try {
            URI uri = new URI(sourceUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Source URL must include a scheme and host");
            }
            String query = uri.getRawQuery();
            String normalizedQuery = query == null
                    ? null
                    : Arrays
                .stream(query.split("&"))
                .filter(parameter -> !parameter.isBlank())
                .filter(parameter -> !isVolatile(parameter.substring(
                        0,
                        parameter.indexOf('=') >= 0 ? parameter.indexOf('=') : parameter.length()
                )))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("&"));
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    uri.getPath(),
                    normalizedQuery == null || normalizedQuery.isBlank() ? null : normalizedQuery,
                    uri.getFragment()
            )
                .toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid source URL", exception);
        }
    }

    private boolean isVolatile(String key) {
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        return normalizedKey.startsWith("utm_") || normalizedKey.equals("fbclid") || normalizedKey.equals("gclid");
    }
}
