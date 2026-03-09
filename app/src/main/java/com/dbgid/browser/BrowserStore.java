package com.dbgid.browser;

import android.content.Context;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BrowserStore {

    private static final String BOOKMARKS_FILE = "bookmarks.json";
    private static final String SCRIPTS_FILE = "user_scripts.json";
    private static final String EXTENSIONS_FILE = "extensions.json";
    private static final String SITE_CONFIG_FILE = "site_configs.json";
    private static final String DOWNLOADS_FILE = "downloads.json";

    private final Context context;

    public BrowserStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public ArrayList<Bookmark> loadBookmarks() {
        ArrayList<Bookmark> result = new ArrayList<Bookmark>();
        JSONArray array = readArray(BOOKMARKS_FILE);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                result.add(Bookmark.fromJson(item));
            }
        }
        return result;
    }

    public void saveBookmarks(ArrayList<Bookmark> bookmarks) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < bookmarks.size(); i++) {
            array.put(bookmarks.get(i).toJson());
        }
        writeArray(BOOKMARKS_FILE, array);
    }

    public ArrayList<UserScript> loadScripts() {
        ArrayList<UserScript> result = new ArrayList<UserScript>();
        JSONArray array = readArray(SCRIPTS_FILE);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                result.add(UserScript.fromJson(item));
            }
        }
        return result;
    }

    public void saveScripts(ArrayList<UserScript> scripts) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < scripts.size(); i++) {
            array.put(scripts.get(i).toJson());
        }
        writeArray(SCRIPTS_FILE, array);
    }

    public ArrayList<ExtensionPackage> loadExtensions() {
        ArrayList<ExtensionPackage> result = new ArrayList<ExtensionPackage>();
        JSONArray array = readArray(EXTENSIONS_FILE);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                result.add(ExtensionPackage.fromJson(item));
            }
        }
        return result;
    }

    public void saveExtensions(ArrayList<ExtensionPackage> extensions) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < extensions.size(); i++) {
            array.put(extensions.get(i).toJson());
        }
        writeArray(EXTENSIONS_FILE, array);
    }

    public LinkedHashMap<String, SiteConfig> loadSiteConfigs() {
        LinkedHashMap<String, SiteConfig> result = new LinkedHashMap<String, SiteConfig>();
        JSONObject root = readObject(SITE_CONFIG_FILE);
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String host = keys.next();
            JSONObject item = root.optJSONObject(host);
            if (item != null) {
                SiteConfig config = SiteConfig.fromJson(item);
                config.host = host;
                result.put(host, config);
            }
        }
        return result;
    }

    public void saveSiteConfigs(LinkedHashMap<String, SiteConfig> configs) {
        JSONObject root = new JSONObject();
        for (String host : configs.keySet()) {
            SiteConfig config = configs.get(host);
            if (config != null) {
                try {
                    root.put(host, config.toJson());
                } catch (JSONException ignored) {}
            }
        }
        writeObject(SITE_CONFIG_FILE, root);
    }

    public ArrayList<DownloadRecord> loadDownloads() {
        ArrayList<DownloadRecord> result = new ArrayList<DownloadRecord>();
        JSONArray array = readArray(DOWNLOADS_FILE);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                result.add(DownloadRecord.fromJson(item));
            }
        }
        return result;
    }

    public void saveDownloads(ArrayList<DownloadRecord> downloads) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < downloads.size(); i++) {
            array.put(downloads.get(i).toJson());
        }
        writeArray(DOWNLOADS_FILE, array);
    }

    public File getExtensionsRoot() {
        File directory = new File(context.getFilesDir(), "extensions");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    public static String createId() {
        return Long.toHexString(System.currentTimeMillis()) + "_" + Long.toHexString((long) (Math.random() * 0xFFFFFF));
    }

    public static String readText(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        } finally {
            closeQuietly(reader, input);
        }
        return builder.toString();
    }

    public static void writeText(File file, String content) throws IOException {
        writeBytes(file, content.getBytes("UTF-8"));
    }

    public static void writeBytes(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(data);
        } finally {
            closeQuietly(output);
        }
    }

    public static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursive(children[i]);
                }
            }
        }
        file.delete();
    }

    private JSONArray readArray(String fileName) {
        try {
            String content = readText(new File(context.getFilesDir(), fileName));
            if (!TextUtils.isEmpty(content)) {
                return new JSONArray(content);
            }
        } catch (Throwable ignored) {}
        return new JSONArray();
    }

    private JSONObject readObject(String fileName) {
        try {
            String content = readText(new File(context.getFilesDir(), fileName));
            if (!TextUtils.isEmpty(content)) {
                return new JSONObject(content);
            }
        } catch (Throwable ignored) {}
        return new JSONObject();
    }

    private void writeArray(String fileName, JSONArray array) {
        try {
            writeText(new File(context.getFilesDir(), fileName), array.toString());
        } catch (Throwable ignored) {}
    }

    private void writeObject(String fileName, JSONObject object) {
        try {
            writeText(new File(context.getFilesDir(), fileName), object.toString());
        } catch (Throwable ignored) {}
    }

    private static void closeQuietly(Closeable... closeables) {
        for (int i = 0; i < closeables.length; i++) {
            try {
                if (closeables[i] != null) {
                    closeables[i].close();
                }
            } catch (IOException ignored) {}
        }
    }

    public static class Bookmark {
        public String title;
        public String url;

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("title", title);
                object.put("url", url);
            } catch (JSONException ignored) {}
            return object;
        }

        public static Bookmark fromJson(JSONObject object) {
            Bookmark bookmark = new Bookmark();
            bookmark.title = object.optString("title");
            bookmark.url = object.optString("url");
            return bookmark;
        }
    }

    public static class UserScript {
        public String id = createId();
        public String name;
        public String matchPattern;
        public String script;
        public boolean enabled = true;

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("name", name);
                object.put("matchPattern", matchPattern);
                object.put("script", script);
                object.put("enabled", enabled);
            } catch (JSONException ignored) {}
            return object;
        }

        public static UserScript fromJson(JSONObject object) {
            UserScript script = new UserScript();
            script.id = readString(object, "id", createId());
            script.name = object.optString("name");
            script.matchPattern = readString(object, "matchPattern", "<all_urls>");
            script.script = object.optString("script");
            script.enabled = object.optBoolean("enabled", true);
            return script;
        }
    }

    public static class SiteConfig {
        public String host;
        public boolean javascriptEnabled = true;
        public boolean desktopMode = false;

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("javascriptEnabled", javascriptEnabled);
                object.put("desktopMode", desktopMode);
            } catch (JSONException ignored) {}
            return object;
        }

        public static SiteConfig fromJson(JSONObject object) {
            SiteConfig config = new SiteConfig();
            config.javascriptEnabled = object.optBoolean("javascriptEnabled", true);
            config.desktopMode = object.optBoolean("desktopMode", false);
            return config;
        }
    }

    public static class ExtensionPackage {
        public String id = createId();
        public String name;
        public String version;
        public String directoryPath;
        public String webStoreId;
        public int manifestVersion;
        public String optionsPage;
        public String popupPage;
        public String backgroundPage;
        public String backgroundServiceWorker;
        public boolean enabled = true;
        public ArrayList<String> backgroundScripts = new ArrayList<String>();
        public ArrayList<ExtensionContentScript> contentScripts = new ArrayList<ExtensionContentScript>();

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray backgroundScriptsArray = new JSONArray();
            JSONArray scriptsArray = new JSONArray();
            for (int i = 0; i < backgroundScripts.size(); i++) {
                backgroundScriptsArray.put(backgroundScripts.get(i));
            }
            for (int i = 0; i < contentScripts.size(); i++) {
                scriptsArray.put(contentScripts.get(i).toJson());
            }
            try {
                object.put("id", id);
                object.put("name", name);
                object.put("version", version);
                object.put("directoryPath", directoryPath);
                object.put("webStoreId", webStoreId);
                object.put("manifestVersion", manifestVersion);
                object.put("optionsPage", optionsPage);
                object.put("popupPage", popupPage);
                object.put("backgroundPage", backgroundPage);
                object.put("backgroundServiceWorker", backgroundServiceWorker);
                object.put("enabled", enabled);
                object.put("backgroundScripts", backgroundScriptsArray);
                object.put("contentScripts", scriptsArray);
            } catch (JSONException ignored) {}
            return object;
        }

        public static ExtensionPackage fromJson(JSONObject object) {
            ExtensionPackage extension = new ExtensionPackage();
            extension.id = readString(object, "id", createId());
            extension.name = object.optString("name");
            extension.version = object.optString("version");
            extension.directoryPath = object.optString("directoryPath");
            extension.webStoreId = object.optString("webStoreId");
            extension.manifestVersion = object.optInt("manifestVersion");
            extension.optionsPage = object.optString("optionsPage");
            extension.popupPage = object.optString("popupPage");
            extension.backgroundPage = object.optString("backgroundPage");
            extension.backgroundServiceWorker = object.optString("backgroundServiceWorker");
            extension.enabled = object.optBoolean("enabled", true);
            JSONArray backgroundScriptsArray = object.optJSONArray("backgroundScripts");
            if (backgroundScriptsArray != null) {
                for (int i = 0; i < backgroundScriptsArray.length(); i++) {
                    extension.backgroundScripts.add(backgroundScriptsArray.optString(i));
                }
            }
            JSONArray scriptsArray = object.optJSONArray("contentScripts");
            if (scriptsArray != null) {
                for (int i = 0; i < scriptsArray.length(); i++) {
                    JSONObject item = scriptsArray.optJSONObject(i);
                    if (item != null) {
                        extension.contentScripts.add(ExtensionContentScript.fromJson(item));
                    }
                }
            }
            return extension;
        }
    }

    public static class ExtensionContentScript {
        public ArrayList<String> matches = new ArrayList<String>();
        public ArrayList<String> jsFiles = new ArrayList<String>();

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray matchesArray = new JSONArray();
            JSONArray filesArray = new JSONArray();
            for (int i = 0; i < matches.size(); i++) {
                matchesArray.put(matches.get(i));
            }
            for (int i = 0; i < jsFiles.size(); i++) {
                filesArray.put(jsFiles.get(i));
            }
            try {
                object.put("matches", matchesArray);
                object.put("jsFiles", filesArray);
            } catch (JSONException ignored) {}
            return object;
        }

        public static ExtensionContentScript fromJson(JSONObject object) {
            ExtensionContentScript script = new ExtensionContentScript();
            JSONArray matchesArray = object.optJSONArray("matches");
            JSONArray filesArray = object.optJSONArray("jsFiles");
            if (matchesArray != null) {
                for (int i = 0; i < matchesArray.length(); i++) {
                    script.matches.add(matchesArray.optString(i));
                }
            }
            if (filesArray != null) {
                for (int i = 0; i < filesArray.length(); i++) {
                    script.jsFiles.add(filesArray.optString(i));
                }
            }
            return script;
        }
    }

    public static class DownloadRecord {
        public String id = createId();
        public String fileName;
        public String url;
        public long createdAt;

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("fileName", fileName);
                object.put("url", url);
                object.put("createdAt", createdAt);
            } catch (JSONException ignored) {}
            return object;
        }

        public static DownloadRecord fromJson(JSONObject object) {
            DownloadRecord record = new DownloadRecord();
            record.id = readString(object, "id", createId());
            record.fileName = object.optString("fileName");
            record.url = object.optString("url");
            record.createdAt = object.optLong("createdAt");
            return record;
        }
    }

    private static String readString(JSONObject object, String key, String fallback) {
        if (object == null) {
            return fallback;
        }
        String value = object.optString(key);
        return TextUtils.isEmpty(value) ? fallback : value;
    }
}
