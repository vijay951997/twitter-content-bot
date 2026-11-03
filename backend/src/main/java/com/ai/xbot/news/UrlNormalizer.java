package com.ai.xbot.news;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Normalises article links so the same story isn't stored twice (drops tracking parameters and fragments). */
public final class UrlNormalizer {

    private UrlNormalizer() {}

    public static String normalize(String url) {
        try {
            URI u = URI.create(url.trim());
            String query = u.getRawQuery() == null ? null : Arrays.stream(u.getRawQuery().split("&"))
                    .filter(p -> !p.isBlank())
                    .filter(p -> {
                        String k = p.split("=", 2)[0].toLowerCase(Locale.ROOT);
                        return !(k.startsWith("utm_") || k.equals("fbclid") || k.equals("gclid") || k.equals("ref")
                                || k.equals("cmp") || k.equals("ito"));
                    })
                    .collect(Collectors.joining("&"));
            String scheme = u.getScheme() == null ? "https" : u.getScheme().toLowerCase(Locale.ROOT);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String port = u.getPort() == -1 ? "" : ":" + u.getPort();
            String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            return scheme + "://" + host + port + path + (query == null || query.isEmpty() ? "" : "?" + query);
        } catch (IllegalArgumentException e) {
            return url.trim();
        }
    }

    /** Registrable-ish domain used to tell independent publishers apart (www. stripped). */
    public static String domain(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) return "";
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
