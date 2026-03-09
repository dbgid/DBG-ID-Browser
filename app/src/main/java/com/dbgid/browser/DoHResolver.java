package com.dbgid.browser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DoHResolver {

    public static final String PROVIDER_CLOUDFLARE = "Cloudflare";
    public static final String PROVIDER_GOOGLE = "Google";
    public static final String PROVIDER_CUSTOM = "Custom";

    public static final String ENDPOINT_CLOUDFLARE = "https://cloudflare-dns.com/dns-query";
    public static final String ENDPOINT_GOOGLE = "https://dns.google/resolve";

    private DoHResolver() {}

    public static LookupResult resolve(String endpoint, String host) throws Exception {
        String cleanEndpoint = endpoint == null ? "" : endpoint.trim();
        String cleanHost = host == null ? "" : host.trim();
        if (cleanEndpoint.length() == 0) {
            throw new IllegalArgumentException("DoH endpoint is required.");
        }
        if (cleanHost.length() == 0) {
            throw new IllegalArgumentException("Host is required.");
        }

        String separator = cleanEndpoint.indexOf('?') >= 0 ? "&" : "?";
        String queryUrl = cleanEndpoint + separator
            + "name=" + URLEncoder.encode(cleanHost, "UTF-8")
            + "&type=A";

        HttpURLConnection connection = null;
        InputStream input = null;
        BufferedReader reader = null;
        try {
            connection = (HttpURLConnection) new URL(queryUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/dns-json");
            connection.setRequestProperty("User-Agent", "DBG-ID-Browser/1.0");
            connection.connect();

            int code = connection.getResponseCode();
            input = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) {
                throw new IllegalStateException("Resolver returned no response body.");
            }
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }

            LookupResult result = new LookupResult();
            result.httpCode = code;
            result.rawJson = raw.toString();

            JSONObject json = new JSONObject(result.rawJson);
            result.status = json.optInt("Status", -1);
            result.truncated = json.optBoolean("TC", false);
            result.recursionDesired = json.optBoolean("RD", false);
            result.recursionAvailable = json.optBoolean("RA", false);
            JSONArray answers = json.optJSONArray("Answer");
            if (answers != null) {
                for (int i = 0; i < answers.length(); i++) {
                    JSONObject item = answers.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    AnswerRecord record = new AnswerRecord();
                    record.name = item.optString("name");
                    record.type = item.optInt("type", 0);
                    record.ttl = item.optInt("TTL", 0);
                    record.data = item.optString("data");
                    result.answers.add(record);
                }
            }
            return result;
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Throwable ignored) {}
            try {
                if (input != null) input.close();
            } catch (Throwable ignored) {}
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static String defaultEndpointForProvider(String provider) {
        if (PROVIDER_GOOGLE.equals(provider)) {
            return ENDPOINT_GOOGLE;
        }
        if (PROVIDER_CUSTOM.equals(provider)) {
            return "";
        }
        return ENDPOINT_CLOUDFLARE;
    }

    public static final class LookupResult {
        public int httpCode;
        public int status;
        public boolean truncated;
        public boolean recursionDesired;
        public boolean recursionAvailable;
        public String rawJson;
        public final ArrayList<AnswerRecord> answers = new ArrayList<AnswerRecord>();
    }

    public static final class AnswerRecord {
        public String name;
        public int type;
        public int ttl;
        public String data;
    }
}
