package com.dbgid.browser;

import android.content.Context;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SetIps {

    private static final String ASSET_NAME = "ip2asn-v4-u32.tsv";
    private static final Random RANDOM = new Random();
    private static Database cachedDatabase;

    private SetIps() {
    }

    public static final class Entry {
        public final String ip;
        public final String country;
        public final String asnName;

        public Entry(String ip, String country, String asnName) {
            this.ip = ip == null ? "" : ip.trim();
            this.country = country == null ? "" : country.trim();
            this.asnName = asnName == null ? "" : asnName.trim();
        }

        public String displayValue() {
            return ip + "|" + country + "|" + asnName;
        }

        public static Entry fromDisplayValue(String value) {
            if (TextUtils.isEmpty(value)) {
                return null;
            }
            String[] parts = value.split("\\|", 3);
            String ip = parts.length > 0 ? parts[0].trim() : "";
            String country = parts.length > 1 ? parts[1].trim() : "";
            String asnName = parts.length > 2 ? parts[2].trim() : "";
            if (TextUtils.isEmpty(ip)) {
                return null;
            }
            return new Entry(ip, country, asnName);
        }
    }

    public static synchronized List<Entry> generate(Context context, int count) throws IOException {
        Database database = loadDatabase(context);
        ArrayList<Entry> result = new ArrayList<Entry>();
        int attempts = 0;
        int maxAttempts = Math.max(200, count * 200);
        while (result.size() < count && attempts < maxAttempts) {
            attempts++;
            int index = RANDOM.nextInt(database.size);
            long start = unsignedInt(database.starts[index]);
            long end = unsignedInt(database.ends[index]);
            if (end < start) {
                long swap = start;
                start = end;
                end = swap;
            }
            long ipValue = randomLongInRange(start, end);
            if (!isPublicIPv4(ipValue)) {
                continue;
            }
            Entry entry = new Entry(longToIp(ipValue), database.countries[index], database.names[index]);
            if (!contains(result, entry.displayValue())) {
                result.add(entry);
            }
        }
        while (result.size() < count) {
            Entry entry = new Entry(randomPublicIPv4Fallback(), "", "");
            if (!contains(result, entry.displayValue())) {
                result.add(entry);
            }
        }
        return result;
    }

    private static boolean contains(List<Entry> entries, String displayValue) {
        for (int i = 0; i < entries.size(); i++) {
            if (displayValue.equals(entries.get(i).displayValue())) {
                return true;
            }
        }
        return false;
    }

    private static synchronized Database loadDatabase(Context context) throws IOException {
        if (cachedDatabase != null) {
            return cachedDatabase;
        }
        InputStream input = context.getAssets().open(ASSET_NAME);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        IntArray starts = new IntArray();
        IntArray ends = new IntArray();
        StringArray countries = new StringArray();
        StringArray names = new StringArray();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", 5);
            if (parts.length < 5) {
                continue;
            }
            try {
                long start = Long.parseLong(parts[0].trim());
                long end = Long.parseLong(parts[1].trim());
                starts.add((int) start);
                ends.add((int) end);
                countries.add(parts[3].trim());
                names.add(parts[4].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        reader.close();
        cachedDatabase = new Database(starts.toArray(), ends.toArray(), countries.toArray(), names.toArray(), starts.size());
        return cachedDatabase;
    }

    private static long randomLongInRange(long min, long max) {
        if (max <= min) {
            return min;
        }
        double span = (double) (max - min + 1L);
        return min + (long) Math.floor(RANDOM.nextDouble() * span);
    }

    private static boolean isPublicIPv4(long value) {
        int a = (int) ((value >> 24) & 0xff);
        int b = (int) ((value >> 16) & 0xff);
        if (a == 0 || a >= 224 || a == 10 || a == 127) {
            return false;
        }
        if (a == 100 && b >= 64 && b <= 127) {
            return false;
        }
        if (a == 169 && b == 254) {
            return false;
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return false;
        }
        if (a == 192 && b == 168) {
            return false;
        }
        return true;
    }

    private static String randomPublicIPv4Fallback() {
        for (int attempts = 0; attempts < 1024; attempts++) {
            long value = ((long) (1 + RANDOM.nextInt(223)) << 24)
                    | ((long) RANDOM.nextInt(256) << 16)
                    | ((long) RANDOM.nextInt(256) << 8)
                    | (long) (1 + RANDOM.nextInt(254));
            if (isPublicIPv4(value)) {
                return longToIp(value);
            }
        }
        return "8.8.8.8";
    }

    private static long unsignedInt(int value) {
        return value & 0xffffffffL;
    }

    private static String longToIp(long value) {
        return ((value >> 24) & 0xff) + "."
                + ((value >> 16) & 0xff) + "."
                + ((value >> 8) & 0xff) + "."
                + (value & 0xff);
    }

    private static final class Database {
        public final int[] starts;
        public final int[] ends;
        public final String[] countries;
        public final String[] names;
        public final int size;

        Database(int[] starts, int[] ends, String[] countries, String[] names, int size) {
            this.starts = starts;
            this.ends = ends;
            this.countries = countries;
            this.names = names;
            this.size = size;
        }
    }

    private static final class IntArray {
        private int[] values = new int[1024];
        private int size;

        void add(int value) {
            if (size >= values.length) {
                int[] expanded = new int[values.length * 2];
                System.arraycopy(values, 0, expanded, 0, values.length);
                values = expanded;
            }
            values[size++] = value;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            int[] out = new int[size];
            System.arraycopy(values, 0, out, 0, size);
            return out;
        }
    }

    private static final class StringArray {
        private String[] values = new String[1024];
        private int size;

        void add(String value) {
            if (size >= values.length) {
                String[] expanded = new String[values.length * 2];
                System.arraycopy(values, 0, expanded, 0, values.length);
                values = expanded;
            }
            values[size++] = value == null ? "" : value;
        }

        String[] toArray() {
            String[] out = new String[size];
            System.arraycopy(values, 0, out, 0, size);
            return out;
        }
    }
}
