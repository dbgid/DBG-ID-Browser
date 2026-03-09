package com.dbgid.browser;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

public final class LaunchPayloadParser {

    private LaunchPayloadParser() {
    }

    public static JSONObject parseIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        JSONObject parsed = parseRaw(intent.getDataString());
        if (parsed != null) {
            return parsed;
        }
        String[] extraKeys = new String[] {
                "data",
                "payload",
                "webdriver",
                "webdriver_payload",
                "launch_payload",
                Intent.EXTRA_TEXT
        };
        for (int i = 0; i < extraKeys.length; i++) {
            String value = intent.getStringExtra(extraKeys[i]);
            parsed = parseRaw(value);
            if (parsed != null) {
                return parsed;
            }
        }
        if (intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
            CharSequence clipText = intent.getClipData().getItemAt(0).getText();
            parsed = parseRaw(clipText == null ? null : clipText.toString());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    public static JSONObject parseIntentData(Uri dataUri) {
        if (dataUri == null) {
            return null;
        }
        return parseRaw(dataUri.toString());
    }

    public static JSONObject parseRaw(String raw) {
        String decoded = decodeRaw(raw);
        if (TextUtils.isEmpty(decoded)) {
            return null;
        }
        try {
            return new JSONObject(decoded);
        } catch (JSONException ignored) {
        }
        try {
            return new JSONObject(normalizePythonLiteral(decoded));
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static String decodeRaw(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        if (!raw.startsWith("webdriver")) {
            return raw;
        }
        String dataParameter = extractDataParameter(raw);
        if (TextUtils.isEmpty(dataParameter)) {
            return raw;
        }
        try {
            return new String(Base64.decode(dataParameter.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return dataParameter;
        }
    }

    private static String extractDataParameter(String url) {
        int dataParamStartIndex = url.indexOf("data=");
        if (dataParamStartIndex != -1) {
            String dataParameterValue = url.substring(dataParamStartIndex + 5);
            int nextParamIndex = dataParameterValue.indexOf('&');
            if (nextParamIndex != -1) {
                dataParameterValue = dataParameterValue.substring(0, nextParamIndex);
            }
            try {
                return URLDecoder.decode(dataParameterValue, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        return null;
    }

    private static String normalizePythonLiteral(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 16);
        boolean insideSingleQuotedString = false;
        boolean escaping = false;

        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (insideSingleQuotedString) {
                if (escaping) {
                    builder.append(escapeJsonChar(current));
                    escaping = false;
                    continue;
                }
                if (current == '\\') {
                    escaping = true;
                    continue;
                }
                if (current == '\'') {
                    builder.append('"');
                    insideSingleQuotedString = false;
                    continue;
                }
                builder.append(escapeJsonChar(current));
                continue;
            }

            if (current == '\'') {
                builder.append('"');
                insideSingleQuotedString = true;
                continue;
            }

            if (startsWithToken(raw, i, "True")) {
                builder.append("true");
                i += 3;
                continue;
            }
            if (startsWithToken(raw, i, "False")) {
                builder.append("false");
                i += 4;
                continue;
            }
            if (startsWithToken(raw, i, "None")) {
                builder.append("null");
                i += 3;
                continue;
            }
            builder.append(current);
        }

        return builder.toString();
    }

    private static boolean startsWithToken(String value, int index, String token) {
        int end = index + token.length();
        if (end > value.length() || !value.regionMatches(index, token, 0, token.length())) {
            return false;
        }
        boolean leftBoundary = index == 0 || isTokenBoundary(value.charAt(index - 1));
        boolean rightBoundary = end == value.length() || isTokenBoundary(value.charAt(end));
        return leftBoundary && rightBoundary;
    }

    private static boolean isTokenBoundary(char value) {
        return Character.isWhitespace(value) || value == ',' || value == ':' || value == '}' || value == ']' || value == '(' || value == '[';
    }

    private static String escapeJsonChar(char value) {
        switch (value) {
            case '\\':
                return "\\\\";
            case '"':
                return "\\\"";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            default:
                return String.valueOf(value);
        }
    }
}
