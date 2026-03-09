package com.dbgid.browser;

import android.net.Uri;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class MatchUtils {

    private MatchUtils() {}

    public static boolean matchesUrl(String pattern, String url) {
        if (TextUtils.isEmpty(pattern) || TextUtils.isEmpty(url)) {
            return false;
        }
        String trimmedPattern = pattern.trim();
        if ("<all_urls>".equals(trimmedPattern)) {
            return true;
        }
        if (trimmedPattern.indexOf("://") >= 0) {
            return matchesChromePattern(trimmedPattern, url);
        }
        if (trimmedPattern.indexOf('*') >= 0) {
            return wildcardMatch(trimmedPattern, url);
        }
        return url.contains(trimmedPattern);
    }

    public static boolean matchesAny(ArrayList<String> patterns, String url) {
        if (patterns == null) {
            return false;
        }
        for (int i = 0; i < patterns.size(); i++) {
            if (matchesUrl(patterns.get(i), url)) {
                return true;
            }
        }
        return false;
    }

    public static String hostFromUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean matchesChromePattern(String pattern, String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getEncodedPath();
            if (TextUtils.isEmpty(path)) {
                path = "/";
            }

            int schemeSeparator = pattern.indexOf("://");
            String schemePattern = pattern.substring(0, schemeSeparator);
            String remainder = pattern.substring(schemeSeparator + 3);
            int slashIndex = remainder.indexOf('/');
            String hostPattern = slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder;
            String pathPattern = slashIndex >= 0 ? remainder.substring(slashIndex) : "/*";

            if (!schemeMatches(schemePattern, scheme)) {
                return false;
            }
            if (!hostMatches(hostPattern, host)) {
                return false;
            }
            return wildcardMatch(pathPattern, path);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean schemeMatches(String schemePattern, String scheme) {
        if (TextUtils.isEmpty(schemePattern)) {
            return false;
        }
        if ("*".equals(schemePattern)) {
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        }
        return schemePattern.equalsIgnoreCase(scheme);
    }

    private static boolean hostMatches(String hostPattern, String host) {
        if ("*".equals(hostPattern)) {
            return true;
        }
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        String normalizedHost = host.toLowerCase();
        String normalizedPattern = hostPattern.toLowerCase();
        if (normalizedPattern.startsWith("*.")) {
            String base = normalizedPattern.substring(2);
            return normalizedHost.equals(base) || normalizedHost.endsWith("." + base);
        }
        return normalizedHost.equals(normalizedPattern);
    }

    private static boolean wildcardMatch(String pattern, String value) {
        StringBuilder regex = new StringBuilder();
        regex.append('^');
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append('$');
        return value.matches(regex.toString());
    }
}
